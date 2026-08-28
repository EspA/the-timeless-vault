package com.thetimelessvault.ebay;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.inventory.Photo;
import com.thetimelessvault.inventory.PhotoRepository;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.orders.SetNumberParser;
import com.thetimelessvault.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class EbayListingImportService {

    private static final Logger log = LoggerFactory.getLogger(EbayListingImportService.class);
    private static final int MAX_PHOTOS = 2;

    private final EbayClient ebay;
    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final CatalogService catalogService;
    private final PhotoRepository photos;
    private final ObjectStorage storage;
    private final TransactionTemplate transactions;
    private final RestClient restClient = RestClient.builder().build();

    public EbayListingImportService(
            EbayClient ebay,
            InventoryItemRepository items,
            ChannelListingRepository listings,
            CatalogService catalogService,
            PhotoRepository photos,
            ObjectStorage storage,
            PlatformTransactionManager transactionManager
    ) {
        this.ebay = ebay;
        this.items = items;
        this.listings = listings;
        this.catalogService = catalogService;
        this.photos = photos;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Report run(boolean dryRun) {
        if (!ebay.sellReady()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Connect eBay in Settings before importing listings");
        }
        List<EbayActiveListing> active = ebay.listActiveSelling();
        List<Row> rows = new ArrayList<>();
        int skipped = 0;
        int linked = 0;
        int created = 0;
        int failed = 0;
        for (EbayActiveListing listing : active) {
            try {
                Row row = dryRun ? preview(listing) : transactions.execute(status -> apply(listing));
                rows.add(row);
                switch (row.action()) {
                    case "SKIP" -> skipped += 1;
                    case "LINK" -> linked += 1;
                    case "CREATE" -> created += 1;
                    default -> failed += 1;
                }
            } catch (RuntimeException e) {
                failed += 1;
                log.warn("eBay listing import failed for {}: {}", listing.listingId(), e.getMessage());
                rows.add(new Row("FAIL", listing.listingId(), listing.sku(), listing.title(), null, null, e.getMessage()));
            }
        }
        return new Report(dryRun, active.size(), skipped, linked, created, failed, rows);
    }

    private Row preview(EbayActiveListing listing) {
        if (alreadyLinked(listing).isPresent()) {
            return row("SKIP", listing, alreadyLinked(listing).get(), "Already in inventory");
        }
        Optional<InventoryItem> match = matchItem(listing);
        if (match.isPresent()) {
            return row("LINK", listing, match.get(), "Match existing SKU");
        }
        return new Row(
                "CREATE",
                listing.listingId(),
                listing.sku(),
                listing.title(),
                setNumber(listing),
                listing.sku(),
                "New inventory item"
        );
    }

    Row apply(EbayActiveListing listing) {
        Optional<InventoryItem> linked = alreadyLinked(listing);
        if (linked.isPresent()) {
            return row("SKIP", listing, linked.get(), "Already in inventory");
        }
        Optional<InventoryItem> match = matchItem(listing);
        if (match.isPresent()) {
            attachListing(match.get(), listing);
            copyPhotos(match.get(), listing);
            return row("LINK", listing, match.get(), "Linked existing SKU");
        }
        InventoryItem created = createItem(listing);
        attachListing(created, listing);
        copyPhotos(created, listing);
        return row("CREATE", listing, created, "Imported from eBay");
    }

    private Optional<InventoryItem> alreadyLinked(EbayActiveListing listing) {
        String url = EbayClient.listingUrl(listing.listingId());
        Optional<ChannelListing> byUrl = listings.findByPlatformAndLiveUrl(Platform.EBAY, url);
        if (byUrl.isPresent()) {
            return Optional.of(byUrl.get().getInventoryItem());
        }
        return listings.findByPlatformAndExternalId(Platform.EBAY, listing.listingId())
                .map(ChannelListing::getInventoryItem);
    }

    private Optional<InventoryItem> matchItem(EbayActiveListing listing) {
        if (listing.sku() == null) {
            return Optional.empty();
        }
        Optional<InventoryItem> bySku = items.findWithCatalogBySkuIgnoreCase(listing.sku());
        if (bySku.isEmpty()) {
            return Optional.empty();
        }
        Optional<ChannelListing> existing = listings.findByInventoryItemIdAndPlatform(bySku.get().getId(), Platform.EBAY);
        if (existing.isPresent() && existing.get().getLiveUrl() != null
                && !existing.get().getLiveUrl().equals(EbayClient.listingUrl(listing.listingId()))) {
            return Optional.empty();
        }
        return bySku;
    }

    private InventoryItem createItem(EbayActiveListing listing) {
        String setNumber = setNumber(listing);
        CatalogItem catalog = catalogService.lookupOrStub(setNumber, listing.title());
        String sku = uniqueSku(listing, catalog);
        InventoryItem item = InventoryItem.create(catalog, sku);
        BigDecimal price = listing.price() == null ? BigDecimal.ZERO : listing.price();
        String title = listing.title() == null || listing.title().isBlank()
                ? ThemeMapper.suggestedTitle(catalog.getTheme(), catalog.getSetNumber(), catalog.getName(), ItemCondition.NEW_SEALED)
                : listing.title();
        item.setTitle(ThemeMapper.limitTitle(title));
        if (listing.description() != null && !listing.description().isBlank()) {
            item.setDescription(com.thetimelessvault.common.DescriptionHtml.sanitize(listing.description()));
        }
        item.setEbayPrice(price);
        item.setBricklinkPrice(price);
        item.setShopifyPrice(price);
        item.setBrickowlPrice(price);
        item.setPrice(price);
        item.setItemType(ItemType.SET);
        item.setCondition(ItemCondition.fromEbay(listing.conditionId(), listing.conditionName()));
        item.applyStockAndQuantity(StockStatus.IN_STOCK, listing.quantity());
        item.setPackageLbs(0);
        item.setPackageOz(0);
        item.setNotes("Imported from existing eBay listing " + listing.listingId());
        item.setEbayStoreCategory(ThemeMapper.ebayStoreCategory(catalog.getTheme()));
        return items.save(item);
    }

    private String uniqueSku(EbayActiveListing listing, CatalogItem catalog) {
        if (listing.sku() != null && items.findWithCatalogBySkuIgnoreCase(listing.sku()).isEmpty()) {
            return listing.sku();
        }
        String generated;
        do {
            generated = "TTV-" + catalog.getSetNumber().replaceAll("[^A-Za-z0-9-]", "") + "-"
                    + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        } while (items.findWithCatalogBySkuIgnoreCase(generated).isPresent());
        return generated;
    }

    private void attachListing(InventoryItem item, EbayActiveListing listing) {
        ChannelListing channel = listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.EBAY)
                .orElseGet(() -> ChannelListing.create(item, Platform.EBAY));
        String offerId = resolveOfferId(listing);
        channel.markPublished(offerId, EbayClient.listingUrl(listing.listingId()), listing.price());
        channel.setEbayStatus("ACTIVE");
        listings.save(channel);
    }

    private String resolveOfferId(EbayActiveListing listing) {
        if (listing.sku() == null) {
            return listing.listingId();
        }
        try {
            String offerId = ebay.findOfferId(listing.sku());
            return offerId == null || offerId.isBlank() ? listing.listingId() : offerId;
        } catch (RuntimeException e) {
            log.info("No eBay offer for sku {}: {}", listing.sku(), e.getMessage());
            return listing.listingId();
        }
    }

    private void copyPhotos(InventoryItem item, EbayActiveListing listing) {
        List<Photo> existing = photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(item.getId());
        if (!existing.isEmpty()) {
            return;
        }
        int stored = 0;
        for (String url : listing.imageUrls()) {
            if (stored >= MAX_PHOTOS) {
                break;
            }
            try {
                byte[] bytes = restClient.get().uri(url).retrieve().body(byte[].class);
                if (bytes == null || bytes.length == 0) {
                    continue;
                }
                String filename = filenameFromUrl(url, stored);
                String contentType = filename.endsWith(".png") ? "image/png" : "image/jpeg";
                String key = item.getId() + "/" + UUID.randomUUID() + extension(filename);
                storage.store(key, new ByteArrayInputStream(bytes), contentType, bytes.length);
                Photo photo = Photo.create(item, key, filename, contentType, stored);
                if (stored == 0) {
                    photo.setPrimaryForBricklink(true);
                }
                photos.save(photo);
                stored += 1;
            } catch (RuntimeException e) {
                log.warn("Could not copy eBay photo {} for {}: {}", url, listing.listingId(), e.getMessage());
            }
        }
    }

    private static String setNumber(EbayActiveListing listing) {
        return SetNumberParser.firstNonBlank(
                SetNumberParser.fromSku(listing.sku()),
                SetNumberParser.fromTitle(listing.title()),
                "UNKNOWN"
        );
    }

    private static Row row(String action, EbayActiveListing listing, InventoryItem item, String message) {
        return new Row(
                action,
                listing.listingId(),
                listing.sku(),
                listing.title(),
                item.getCatalogItem() == null ? setNumber(listing) : item.getCatalogItem().getSetNumber(),
                item.getSku(),
                message
        );
    }

    private static String filenameFromUrl(String url, int index) {
        int slash = url.lastIndexOf('/');
        String last = slash >= 0 ? url.substring(slash + 1) : "photo-" + index + ".jpg";
        int query = last.indexOf('?');
        if (query >= 0) {
            last = last.substring(0, query);
        }
        return last.isBlank() ? "photo-" + index + ".jpg" : last;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".jpg";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    public record Report(
            boolean dryRun,
            int fetched,
            int skipped,
            int linked,
            int created,
            int failed,
            List<Row> rows
    ) {
    }

    public record Row(
            String action,
            String listingId,
            String ebaySku,
            String title,
            String setNumber,
            String inventorySku,
            String message
    ) {
    }
}
