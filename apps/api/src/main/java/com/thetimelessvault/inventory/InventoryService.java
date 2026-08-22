package com.thetimelessvault.inventory;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.common.ListingStatus;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.market.MarketScanLauncher;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.publish.PublishJobRepository;
import com.thetimelessvault.storage.ObjectStorage;
import com.thetimelessvault.watch.SetWatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryItemRepository items;
    private final PhotoRepository photos;
    private final ChannelListingRepository listings;
    private final PublishJobRepository publishJobs;
    private final PriceGuardRepository priceGuards;
    private final CatalogService catalogService;
    private final ObjectStorage storage;
    private final SetWatchService setWatches;
    private final MarketScanLauncher marketScans;

    public InventoryService(
            InventoryItemRepository items,
            PhotoRepository photos,
            ChannelListingRepository listings,
            PublishJobRepository publishJobs,
            PriceGuardRepository priceGuards,
            CatalogService catalogService,
            ObjectStorage storage,
            SetWatchService setWatches,
            MarketScanLauncher marketScans
    ) {
        this.items = items;
        this.photos = photos;
        this.listings = listings;
        this.publishJobs = publishJobs;
        this.priceGuards = priceGuards;
        this.catalogService = catalogService;
        this.storage = storage;
        this.setWatches = setWatches;
        this.marketScans = marketScans;
    }

    @Transactional
    public InventoryItem create(InventoryDtos.CreateRequest request) {
        CatalogItem catalog = catalogService.lookup(request.setNumber(), false);
        String sku = "TTV-" + catalog.getSetNumber().replaceAll("[^A-Za-z0-9-]", "") + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        InventoryItem item = InventoryItem.create(catalog, sku);
        applyCreate(item, catalog, request);
        InventoryItem saved = items.save(item);
        if (setWatches.ensureWatch(catalog, false)) {
            scanWatchAfterCommit(catalog.getId());
        }
        return saved;
    }

    private void scanWatchAfterCommit(UUID catalogId) {
        Runnable scan = () -> marketScans.scanBothManual(catalogId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            scan.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scan.run();
            }
        });
    }

    @Transactional
    public InventoryItem update(UUID id, InventoryDtos.UpdateRequest request) {
        InventoryItem item = get(id);
        if (request.title() != null) {
            item.setTitle(ThemeMapper.limitTitle(request.title()));
        }
        if (request.description() != null) {
            item.setDescription(com.thetimelessvault.common.DescriptionHtml.sanitize(request.description()));
        }
        if (request.shortDescription() != null) {
            item.setShortDescription(com.thetimelessvault.common.DescriptionHtml.forBrickLink(request.shortDescription()));
        }
        if (request.price() != null) {
            item.setPrice(request.price());
        }
        if (request.ebayPrice() != null) {
            item.setEbayPrice(request.ebayPrice());
            item.setPrice(request.ebayPrice());
        }
        if (request.bricklinkPrice() != null) {
            item.setBricklinkPrice(request.bricklinkPrice());
        }
        if (request.shopifyPrice() != null) {
            item.setShopifyPrice(request.shopifyPrice());
        }
        item.applyStockAndQuantity(request.stockStatus(), request.quantity());
        if (request.cost() != null) {
            item.setCost(request.cost());
        }
        if (request.itemType() != null) {
            item.setItemType(request.itemType());
        }
        if (request.condition() != null) {
            item.setCondition(request.condition());
        }
        if (request.shopifyCollectionIds() != null) {
            item.setShopifyCollectionIds(String.join(",", request.shopifyCollectionIds()));
        }
        if (request.ebayStoreCategory() != null) {
            String category = request.ebayStoreCategory().trim();
            item.setEbayStoreCategory(category.isEmpty() ? null : category);
        }
        if (request.minimumOffer() != null) {
            item.setMinimumOffer(request.minimumOffer());
        }
        if (request.packageLbs() != null) {
            item.setPackageLbs(request.packageLbs());
        }
        if (request.packageOz() != null) {
            item.setPackageOz(request.packageOz());
        }
        if (request.packageLength() != null) {
            item.setPackageLength(request.packageLength());
        }
        if (request.packageWidth() != null) {
            item.setPackageWidth(request.packageWidth());
        }
        if (request.packageHeight() != null) {
            item.setPackageHeight(request.packageHeight());
        }
        if (request.notes() != null) {
            item.setNotes(request.notes());
        }
        item.touch();
        return items.save(item);
    }

    @Transactional
    public void delete(UUID id) {
        InventoryItem item = get(id);
        List<Photo> itemPhotos = photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(id);
        itemPhotos.forEach(photo -> storage.delete(photo.getStorageKey()));
        photos.deleteAll(itemPhotos);
        List<ChannelListing> itemListings = listings.findByInventoryItemId(id);
        itemListings.forEach(listing -> priceGuards.findByChannelListingId(listing.getId()).ifPresent(priceGuards::delete));
        publishJobs.deleteAll(publishJobs.findByInventoryItemIdOrderByCreatedAtDesc(id));
        listings.deleteAll(itemListings);
        items.delete(item);
    }

    public InventoryItem get(UUID id) {
        return items.findWithCatalogById(id).orElseThrow(() -> ApiException.notFound("Inventory item not found"));
    }

    public List<InventoryItem> list() {
        return items.findAllWithCatalog();
    }

    public Page<InventoryItem> list(int page, int size, InventorySpecifications.Query query) {
        int pageSize = Math.min(10_000, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        return items.findAll(InventorySpecifications.matching(query), PageRequest.of(pageIndex, pageSize));
    }

    @Transactional
    public Photo addPhoto(UUID itemId, MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("Photo file is empty");
        }
        InventoryItem item = get(itemId);
        List<Photo> existing = photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(itemId);
        String ext = extension(file.getOriginalFilename());
        String key = item.getId() + "/" + UUID.randomUUID() + ext;
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read uploaded photo");
        }
        Photo photo = Photo.create(item, key, file.getOriginalFilename(), file.getContentType(), existing.size());
        if (existing.isEmpty()) {
            photo.setPrimaryForBricklink(true);
        }
        return photos.save(photo);
    }

    @Transactional
    public void deletePhoto(UUID itemId, UUID photoId) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> ApiException.notFound("Photo not found"));
        if (!photo.getInventoryItem().getId().equals(itemId)) {
            throw ApiException.notFound("Photo not found");
        }
        boolean wasPrimary = photo.isPrimaryForBricklink();
        storage.delete(photo.getStorageKey());
        photos.delete(photo);
        photos.flush();
        if (wasPrimary) {
            List<Photo> remaining = photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(itemId);
            if (!remaining.isEmpty()) {
                remaining.getFirst().setPrimaryForBricklink(true);
                photos.save(remaining.getFirst());
            }
        }
    }

    @Transactional
    public void markPrimary(UUID itemId, UUID photoId) {
        List<Photo> existing = photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(itemId);
        boolean found = false;
        for (Photo photo : existing) {
            boolean primary = photo.getId().equals(photoId);
            photo.setPrimaryForBricklink(primary);
            if (primary) {
                found = true;
            }
        }
        if (!found) {
            throw ApiException.notFound("Photo not found");
        }
        photos.saveAll(existing);
    }

    public List<Photo> photosFor(UUID itemId) {
        return photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(itemId);
    }

    public InventoryDtos.InventoryView toView(InventoryItem item) {
        List<InventoryDtos.PhotoView> photoViews = photosFor(item.getId()).stream()
                .map(photo -> new InventoryDtos.PhotoView(
                        photo.getId(),
                        storage.publicUrl(photo.getStorageKey()),
                        photo.getOriginalFilename(),
                        photo.getSortOrder(),
                        photo.isPrimaryForBricklink()
                ))
                .toList();
        List<String> collections = item.getShopifyCollectionIds() == null || item.getShopifyCollectionIds().isBlank()
                ? List.of()
                : Arrays.stream(item.getShopifyCollectionIds().split(",")).filter(s -> !s.isBlank()).toList();
        ChannelListing shopifyListing = listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)
                .filter(listing -> listing.getStatus() == ListingStatus.PUBLISHED)
                .orElse(null);
        ChannelListing bricklinkListing = listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK)
                .filter(listing -> listing.getStatus() == ListingStatus.PUBLISHED)
                .orElse(null);
        ChannelListing ebayListing = listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.EBAY)
                .filter(listing -> listing.getStatus() == ListingStatus.PUBLISHED)
                .orElse(null);
        return new InventoryDtos.InventoryView(
                item.getId(),
                item.getSku(),
                InventoryDtos.CatalogView.from(item.getCatalogItem()),
                item.getTitle(),
                item.getDescription(),
                item.getShortDescription(),
                item.getPrice(),
                item.priceFor(Platform.EBAY),
                item.priceFor(Platform.BRICKLINK),
                item.priceFor(Platform.SHOPIFY),
                item.getQuantity(),
                item.getStockStatus(),
                item.getCost(),
                item.getItemType(),
                item.getCondition(),
                collections,
                item.getEbayStoreCategory(),
                item.getMinimumOffer(),
                item.getPackageLbs(),
                item.getPackageOz(),
                item.getPackageLength(),
                item.getPackageWidth(),
                item.getPackageHeight(),
                item.getNotes(),
                shopifyListing == null ? null : shopifyListing.getShopifyStatus(),
                bricklinkListing == null ? null : bricklinkListing.getBricklinkStatus(),
                ebayListing == null ? null : ebayListing.getEbayStatus(),
                shopifyListing == null ? null : shopifyListing.getLiveUrl(),
                bricklinkListing == null ? null : bricklinkListing.getLiveUrl(),
                ebayListing == null ? null : ebayListing.getLiveUrl(),
                photoViews,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    public List<String> photoUrls(InventoryItem item) {
        return photosFor(item.getId()).stream()
                .map(photo -> storage.publicUrl(photo.getStorageKey()))
                .toList();
    }

    private void applyCreate(InventoryItem item, CatalogItem catalog, InventoryDtos.CreateRequest request) {
        ItemCondition condition = request.condition() == null ? ItemCondition.NEW_SEALED : request.condition();
        item.setCondition(condition);
        item.setItemType(request.itemType() == null ? ItemType.SET : request.itemType());
        item.setTitle(ThemeMapper.limitTitle(request.title() == null || request.title().isBlank()
                ? ThemeMapper.suggestedTitle(catalog.getTheme(), catalog.getSetNumber(), catalog.getName(), condition)
                : request.title()));
        item.setDescription(com.thetimelessvault.common.DescriptionHtml.sanitize(request.description()));
        item.setShortDescription(com.thetimelessvault.common.DescriptionHtml.forBrickLink(request.shortDescription()));
        item.setEbayPrice(request.ebayPrice());
        item.setBricklinkPrice(request.bricklinkPrice());
        item.setShopifyPrice(request.shopifyPrice());
        item.setPrice(request.ebayPrice());
        item.applyStockAndQuantity(
                request.stockStatus() == null ? StockStatus.IN_TRANSIT : request.stockStatus(),
                request.quantity()
        );
        item.setCost(request.cost());
        item.setShopifyCollectionIds(request.shopifyCollectionIds() == null ? "" : String.join(",", request.shopifyCollectionIds()));
        item.setEbayStoreCategory(request.ebayStoreCategory() == null || request.ebayStoreCategory().isBlank()
                ? ThemeMapper.ebayStoreCategory(catalog.getTheme())
                : request.ebayStoreCategory());
        item.setMinimumOffer(request.minimumOffer());
        item.setPackageLbs(request.packageLbs() == null ? 0 : request.packageLbs());
        item.setPackageOz(request.packageOz() == null ? 0 : request.packageOz());
        item.setPackageLength(request.packageLength());
        item.setPackageWidth(request.packageWidth());
        item.setPackageHeight(request.packageHeight());
        item.setNotes(request.notes());
    }

    private static String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
}
