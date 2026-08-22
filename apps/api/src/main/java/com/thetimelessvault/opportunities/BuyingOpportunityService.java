package com.thetimelessvault.opportunities;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.Photo;
import com.thetimelessvault.inventory.PhotoRepository;
import com.thetimelessvault.market.MarketListing;
import com.thetimelessvault.market.ScanTrigger;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.sales.Sale;
import com.thetimelessvault.settings.NotificationMailer;
import com.thetimelessvault.storage.ObjectStorage;
import com.thetimelessvault.watch.SetWatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BuyingOpportunityService {

    private final BuyingOpportunityRepository opportunities;
    private final NotificationMailer notificationMailer;
    private final AppProperties properties;
    private final PhotoRepository photos;
    private final ObjectStorage storage;

    public BuyingOpportunityService(
            BuyingOpportunityRepository opportunities,
            NotificationMailer notificationMailer,
            AppProperties properties,
            PhotoRepository photos,
            ObjectStorage storage
    ) {
        this.opportunities = opportunities;
        this.notificationMailer = notificationMailer;
        this.properties = properties;
        this.photos = photos;
        this.storage = storage;
    }

    public List<BuyingOpportunity> list() {
        return opportunities.findAllByOrderByCreatedAtDesc();
    }

    public Page<BuyingOpportunity> list(int page, int size) {
        int pageSize = Math.min(10_000, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        return opportunities.findAllByOrderByCreatedAtDesc(PageRequest.of(pageIndex, pageSize));
    }

    public long unreadCount() {
        return opportunities.countByReadAtIsNull();
    }

    @Transactional
    public BuyingOpportunity markRead(UUID id) {
        BuyingOpportunity opportunity = opportunities.findById(id)
                .orElseThrow(() -> ApiException.notFound("Buying opportunity not found"));
        opportunity.setReadAt(Instant.now());
        return opportunities.save(opportunity);
    }

    @Transactional
    public int markAllRead() {
        Instant now = Instant.now();
        List<BuyingOpportunity> unread = opportunities.findByReadAtIsNull();
        unread.forEach(opportunity -> opportunity.setReadAt(now));
        opportunities.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void deleteNewListings(UUID catalogItemId) {
        if (catalogItemId == null) {
            return;
        }
        opportunities.deleteByCatalogItem_IdAndType(catalogItemId, BuyingOpportunity.TYPE_BUYING_OPPORTUNITY);
    }

    public boolean matches(SetWatch watch, MarketListing listing) {
        return watch != null && watch.isEnabled() && watch.acceptsPrice(listing.getPrice());
    }

    @Transactional
    public void recordNewListing(CatalogItem catalog, Platform platform, MarketListing listing, SetWatch watch) {
        recordNewListing(catalog, platform, listing, watch, null);
    }

    @Transactional
    public void recordNewListing(
            CatalogItem catalog,
            Platform platform,
            MarketListing listing,
            SetWatch watch,
            BigDecimal median
    ) {
        if (!matches(watch, listing)) {
            return;
        }
        String dedupe = "NEW:" + platform + ":" + catalog.getId() + ":" + listing.getFingerprint();
        if (opportunities.findByDedupeKeyAndScanTrigger(dedupe, ScanTrigger.AUTOMATIC).isPresent()) {
            return;
        }
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_BUYING_OPPORTUNITY,
                "New " + platform + " listing for " + catalog.getSetNumber() + " " + catalog.getName(),
                dedupe);
        opportunity.setCatalogItem(catalog);
        opportunity.setPlatform(platform);
        opportunity.setScanTrigger(ScanTrigger.AUTOMATIC);
        opportunity.setUrl(listing.getUrl());
        String percent = NotificationEmailRenderer.percentVsMedian(listing.getPrice(), median);
        opportunity.setBody("Price " + NotificationEmailRenderer.money(listing.getPrice())
                + (percent.isBlank() ? "" : " (" + percent + ")")
                + " · " + (listing.getTitle() == null ? "" : listing.getTitle()));
        opportunities.save(opportunity);
        Instant scannedAt = listing.getSnapshot() == null ? opportunity.getCreatedAt() : listing.getSnapshot().getScannedAt();
        email(opportunity, listing.getPrice(), median, listing.getImageUrl(), null, scannedAt, listing);
    }

    @Transactional
    public void recordPriceGuard(
            ChannelListing listing,
            String type,
            BigDecimal yours,
            BigDecimal market,
            BigDecimal highPercent,
            BigDecimal lowPercent,
            Instant scannedAt
    ) {
        String dedupe = type + ":" + listing.getId() + ":" + market.stripTrailingZeros().toPlainString();
        if (opportunities.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        var catalog = listing.getInventoryItem().getCatalogItem();
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                type,
                listing.getPlatform() + " price for " + catalog.getSetNumber() + " is "
                        + (BuyingOpportunity.TYPE_PRICE_HIGH.equals(type) ? "above" : "below") + " market",
                dedupe);
        opportunity.setCatalogItem(catalog);
        opportunity.setChannelListing(listing);
        opportunity.setPlatform(listing.getPlatform());
        opportunity.setUrl(listingUrl(listing));
        String percent = NotificationEmailRenderer.percentVsMedian(yours, market);
        opportunity.setBody("Your price " + NotificationEmailRenderer.money(yours)
                + (percent.isBlank() ? "" : " (" + percent + ")")
                + " vs median " + NotificationEmailRenderer.money(market)
                + " (thresholds +" + highPercent + "% / -" + lowPercent + "%). Adjust manually.");
        opportunities.save(opportunity);
        email(opportunity, yours, market, inventoryPhoto(listing.getInventoryItem()), listing.getInventoryItem(), scannedAt, null);
    }

    @Transactional
    public void recordNewSale(Sale sale, InventoryItem item) {
        if (sale == null || item == null) {
            return;
        }
        String dedupe = "SALE:" + sale.getPlatform() + ":" + sale.getExternalOrderId() + ":" + sale.getExternalLineId();
        if (opportunities.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        CatalogItem catalog = item.getCatalogItem();
        String setNumber = catalog == null ? sale.getSetNumber() : catalog.getSetNumber();
        String setName = catalog == null ? sale.getItemTitle() : catalog.getName();
        String heading = ((setNumber == null ? "" : setNumber) + " " + (setName == null ? "" : setName)).trim();
        String channel = sale.getPlatform() == null
                ? "channel"
                : NotificationEmailRenderer.platformLabel(sale.getPlatform().name());
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_NEW_SALE,
                "New " + channel + " sale" + (heading.isBlank() ? "" : " of " + heading),
                dedupe
        );
        opportunity.setCatalogItem(catalog);
        opportunity.setPlatform(sale.getPlatform());
        String orderUrl = sale.getOrderUrl();
        if (orderUrl != null && !orderUrl.isBlank()) {
            opportunity.setUrl(orderUrl);
        } else if (item.getId() != null) {
            opportunity.setUrl("/inventory/" + item.getId());
        }
        String qty = sale.getQuantity() <= 0 ? "1" : String.valueOf(sale.getQuantity());
        opportunity.setBody(qty + " × " + NotificationEmailRenderer.money(sale.getUnitPrice())
                + (sale.getSku() == null || sale.getSku().isBlank() ? "" : " · " + sale.getSku())
                + (sale.getExternalOrderId() == null || sale.getExternalOrderId().isBlank()
                ? "" : " · order " + sale.getExternalOrderId()));
        opportunities.save(opportunity);
        email(opportunity, sale.getUnitPrice(), null, inventoryPhoto(item), item, sale.getSoldAt(), null);
    }

    static String listingUrl(ChannelListing listing) {
        String liveUrl = listing.getLiveUrl();
        if (usableHttpUrl(liveUrl) && !brokenBrickLinkStoreUrl(liveUrl)) {
            return liveUrl;
        }
        String photoUrl = listing.getBricklinkPhotoUploadUrl();
        if (usableHttpUrl(photoUrl)) {
            return photoUrl;
        }
        if (listing.getInventoryItem() != null && listing.getInventoryItem().getId() != null) {
            return "/inventory/" + listing.getInventoryItem().getId();
        }
        return liveUrl;
    }

    private static boolean usableHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://");
    }

    private static boolean brokenBrickLinkStoreUrl(String url) {
        return url.contains("store.page?p=#");
    }

    private void email(
            BuyingOpportunity opportunity,
            BigDecimal price,
            BigDecimal median,
            String photoUrl,
            InventoryItem inventoryItem,
            Instant scannedAt,
            MarketListing listing
    ) {
        CatalogItem catalog = opportunity.getCatalogItem();
        String listingUrl = absoluteUrl(opportunity.getUrl());
        String inventoryUrl = inventoryItem == null || inventoryItem.getId() == null
                ? null
                : absoluteUrl("/inventory/" + inventoryItem.getId());
        Instant when = scannedAt != null ? scannedAt : opportunity.getCreatedAt();
        NotificationEmail email = new NotificationEmail(
                opportunity.getType(),
                catalog == null ? "" : catalog.getSetNumber(),
                catalog == null ? "" : catalog.getName(),
                photoUrl,
                NotificationEmailRenderer.money(price),
                NotificationEmailRenderer.percentVsMedian(price, median),
                listingUrl,
                inventoryUrl,
                opportunity.getPlatform() == null ? null : opportunity.getPlatform().name(),
                listing == null ? null : listing.getSeller(),
                sellerMeta(opportunity.getPlatform(), listing),
                when
        );
        if (notificationMailer.sendQuietly(
                NotificationEmailRenderer.subject(email),
                NotificationEmailRenderer.text(email),
                NotificationEmailRenderer.html(email, properties.getBaseUrl())
        )) {
            opportunity.setEmailedAt(Instant.now());
            opportunities.save(opportunity);
        }
    }

    static String sellerMeta(Platform platform, MarketListing listing) {
        if (listing == null) {
            return null;
        }
        if (platform == Platform.EBAY) {
            String feedback = NotificationEmailRenderer.feedback(
                    listing.getSellerFeedbackScore(),
                    listing.getSellerFeedbackPercentage()
            );
            return feedback.isBlank() ? null : feedback;
        }
        if (platform == Platform.BRICKLINK) {
            String country = listing.getSellerCountry();
            if (country == null || country.isBlank()) {
                return null;
            }
            return "Country " + country.trim();
        }
        return null;
    }

    private String inventoryPhoto(InventoryItem item) {
        if (item == null || item.getId() == null) {
            return null;
        }
        List<Photo> itemPhotos = photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(item.getId());
        if (itemPhotos.isEmpty()) {
            return null;
        }
        return storage.publicUrl(itemPhotos.getFirst().getStorageKey());
    }

    private String absoluteUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        if (base.isBlank()) {
            return trimmed;
        }
        return trimmed.startsWith("/") ? base + trimmed : base + "/" + trimmed;
    }
}
