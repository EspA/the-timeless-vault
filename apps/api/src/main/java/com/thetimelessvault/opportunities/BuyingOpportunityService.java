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
import com.thetimelessvault.inbound.PurchaseOrder;
import com.thetimelessvault.inbound.PurchaseOrderLine;
import com.thetimelessvault.orders.Order;
import com.thetimelessvault.orders.OrderLine;
import com.thetimelessvault.orders.OrderRepository;
import com.thetimelessvault.settings.NotificationMailer;
import com.thetimelessvault.storage.ObjectStorage;
import com.thetimelessvault.watch.SetWatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class BuyingOpportunityService {

    private final BuyingOpportunityRepository opportunities;
    private final OrderRepository orders;
    private final NotificationMailer notificationMailer;
    private final AppProperties properties;
    private final PhotoRepository photos;
    private final ObjectStorage storage;

    public BuyingOpportunityService(
            BuyingOpportunityRepository opportunities,
            OrderRepository orders,
            NotificationMailer notificationMailer,
            AppProperties properties,
            PhotoRepository photos,
            ObjectStorage storage
    ) {
        this.opportunities = opportunities;
        this.orders = orders;
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
    public BuyingOpportunity markUnread(UUID id) {
        BuyingOpportunity opportunity = opportunities.findById(id)
                .orElseThrow(() -> ApiException.notFound("Buying opportunity not found"));
        opportunity.setReadAt(null);
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
        email(opportunity, listing.getPrice(), median, listing.getImageUrl(), null, scannedAt, listing, null);
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
        email(opportunity, yours, market, inventoryPhoto(listing.getInventoryItem()), listing.getInventoryItem(), scannedAt, null, null);
    }

    @Transactional
    public void recordNewSale(Order order, InventoryItem item) {
        if (order == null || item == null) {
            return;
        }
        OrderLine line = order.lineFor(item);
        String lineId = line == null ? order.getExternalLineId() : line.getExternalLineId();
        String dedupe = "SALE:" + order.getPlatform() + ":" + order.getExternalOrderId() + ":" + lineId;
        if (opportunities.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        CatalogItem catalog = item.getCatalogItem();
        String setNumber = catalog == null
                ? (line == null ? order.getSetNumber() : line.getSetNumber())
                : catalog.getSetNumber();
        String setName = catalog == null
                ? (line == null ? order.getItemTitle() : line.getItemTitle())
                : catalog.getName();
        String heading = ((setNumber == null ? "" : setNumber) + " " + (setName == null ? "" : setName)).trim();
        String channel = order.getPlatform() == null
                ? "channel"
                : NotificationEmailRenderer.platformLabel(order.getPlatform().name());
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_NEW_SALE,
                "New " + channel + " sale" + (heading.isBlank() ? "" : " of " + heading),
                dedupe
        );
        opportunity.setCatalogItem(catalog);
        opportunity.setPlatform(order.getPlatform());
        if (order.getId() != null) {
            opportunity.setUrl("/orders/" + order.getId());
        } else if (item.getId() != null) {
            opportunity.setUrl("/inventory/" + item.getId());
        }
        int quantity = line == null ? order.getQuantity() : line.getQuantity();
        BigDecimal unitPrice = line == null ? order.getUnitPrice() : line.getUnitPrice();
        String sku = line == null ? order.getSku() : line.getSku();
        String qty = quantity <= 0 ? "1" : String.valueOf(quantity);
        opportunity.setBody(qty + " × " + NotificationEmailRenderer.money(unitPrice)
                + (sku == null || sku.isBlank() ? "" : " · " + sku)
                + (order.getExternalOrderId() == null || order.getExternalOrderId().isBlank()
                ? "" : " · order " + order.getExternalOrderId()));
        opportunities.save(opportunity);
        email(opportunity, unitPrice, null, inventoryPhoto(item), item, order.getSoldAt(), null, null);
    }

    @Transactional
    public void recordOrderDelivered(Order order, InventoryItem item) {
        if (order == null || order.getId() == null) {
            return;
        }
        String dedupe = "DELIVERED:" + order.getId();
        if (opportunities.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        CatalogItem catalog = item == null ? null : item.getCatalogItem();
        int lineCount = order.getLines() == null ? 0 : order.getLines().size();
        String setNumber = catalog == null ? order.getSetNumber() : catalog.getSetNumber();
        String setName = catalog == null ? order.getItemTitle() : catalog.getName();
        String heading = lineCount > 1
                ? lineCount + " items"
                : ((setNumber == null ? "" : setNumber) + " " + (setName == null ? "" : setName)).trim();
        String channel = order.getPlatform() == null
                ? "channel"
                : NotificationEmailRenderer.platformLabel(order.getPlatform().name());
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_ORDER_DELIVERED,
                "Delivered " + channel + " order" + (heading.isBlank() ? "" : " of " + heading),
                dedupe
        );
        opportunity.setCatalogItem(catalog);
        opportunity.setPlatform(order.getPlatform());
        opportunity.setUrl("/orders/" + order.getId());
        String qty = order.getQuantity() <= 0 ? "1" : String.valueOf(order.getQuantity());
        StringBuilder body = new StringBuilder();
        if (lineCount > 1) {
            body.append(lineCount).append(" lines · ").append(NotificationEmailRenderer.money(order.merchandiseTotal()));
        } else {
            body.append(qty).append(" × ").append(NotificationEmailRenderer.money(order.getUnitPrice()));
        }
        if (order.getSku() != null && !order.getSku().isBlank() && lineCount <= 1) {
            body.append(" · ").append(order.getSku());
        }
        if (order.getExternalOrderId() != null && !order.getExternalOrderId().isBlank()) {
            body.append(" · order ").append(order.getExternalOrderId());
        }
        opportunity.setBody(body.toString());
        opportunities.save(opportunity);
        email(
                opportunity,
                order.getUnitPrice(),
                null,
                inventoryPhoto(item),
                item,
                order.getStatusUpdatedAt(),
                null,
                trackingDetail(order)
        );
    }

    @Transactional
    public void recordPurchaseOrderDelivered(PurchaseOrder order, InventoryItem item) {
        if (order == null || order.getId() == null) {
            return;
        }
        String dedupe = "PO_DELIVERED:" + order.getId();
        if (opportunities.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        CatalogItem catalog = item == null ? null : item.getCatalogItem();
        String supplierName = order.getSupplier() == null || order.getSupplier().getName() == null
                ? ""
                : order.getSupplier().getName().trim();
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_PURCHASE_ORDER_DELIVERED,
                "Delivered purchase order " + order.displayNumber()
                        + (supplierName.isBlank() ? "" : " from " + supplierName),
                dedupe
        );
        opportunity.setCatalogItem(catalog);
        opportunity.setUrl("/purchase-orders/" + order.getId());
        opportunity.setBody(purchaseOrderBody(order, supplierName));
        opportunities.save(opportunity);
        NotificationEmail email = new NotificationEmail(
                opportunity.getType(),
                order.displayNumber(),
                supplierName,
                inventoryPhoto(item),
                NotificationEmailRenderer.money(order.totalValue()),
                "",
                absoluteUrl(opportunity.getUrl()),
                null,
                order.getCarrier() == null ? null : order.getCarrier().name(),
                null,
                null,
                order.getUpdatedAt(),
                trackingDetail(order)
        );
        sendEmail(opportunity, email);
    }

    static String purchaseOrderBody(PurchaseOrder order, String supplierName) {
        StringBuilder body = new StringBuilder();
        body.append(NotificationEmailRenderer.money(order.totalValue()));
        int lines = order.getLines() == null ? 0 : order.getLines().size();
        if (lines == 1) {
            PurchaseOrderLine line = order.getLines().getFirst();
            String heading = ((line.getSetNumber() == null ? "" : line.getSetNumber()) + " "
                    + (line.getTitle() == null ? "" : line.getTitle())).trim();
            if (!heading.isBlank()) {
                body.append(" · ").append(heading);
            }
        } else if (lines > 1) {
            body.append(" · ").append(lines).append(" lines");
        }
        if (supplierName != null && !supplierName.isBlank()) {
            body.append(" · ").append(supplierName);
        }
        return body.toString();
    }

    static String trackingDetail(Order order) {
        if (order == null) {
            return null;
        }
        return trackingDetail(order.getTrackingNumber(), order.getShippingProvider());
    }

    static String trackingDetail(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        String carrier = order.getCarrier() == null ? null : NotificationEmailRenderer.platformLabel(order.getCarrier().name());
        return trackingDetail(order.getTrackingNumber(), carrier);
    }

    static String trackingDetail(String trackingNumber, String provider) {
        String tracking = trackingNumber == null ? "" : trackingNumber.trim();
        String label = provider == null ? "" : provider.trim();
        if (tracking.isBlank() && label.isBlank()) {
            return null;
        }
        if (tracking.isBlank()) {
            return label;
        }
        if (label.isBlank()) {
            return "Tracking " + tracking;
        }
        return "Tracking " + tracking + " · " + label;
    }

    @Transactional
    public void recordScanFailure(CatalogItem catalog, Platform platform, String message, ScanTrigger trigger) {
        if (trigger != ScanTrigger.AUTOMATIC) {
            return;
        }
        String reason = message == null || message.isBlank() ? "Scan failed." : message.trim();
        String day = LocalDate.now(ZoneId.of("America/New_York")).toString();
        String catalogKey = catalog == null || catalog.getId() == null ? "JOB" : catalog.getId().toString();
        String platformKey = platform == null ? "ALL" : platform.name();
        String dedupe = "SCAN_FAIL:" + platformKey + ":" + catalogKey + ":" + day;
        if (opportunities.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        String channel = platform == null ? "market" : NotificationEmailRenderer.platformLabel(platform.name());
        String heading = catalogHeading(catalog);
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_SCAN_FAILED,
                heading.isBlank()
                        ? "Automatic " + channel + " scan failed"
                        : "Automatic " + channel + " scan failed for " + heading,
                dedupe
        );
        opportunity.setCatalogItem(catalog);
        opportunity.setPlatform(platform);
        opportunity.setScanTrigger(ScanTrigger.AUTOMATIC);
        opportunity.setBody(reason);
        opportunity.setUrl(catalog == null || catalog.getId() == null
                ? "/scan-logs"
                : "/market/" + catalog.getId());
        opportunities.save(opportunity);
        email(opportunity, null, null, null, null, Instant.now(), null, reason);
    }

    static String catalogHeading(CatalogItem catalog) {
        if (catalog == null) {
            return "";
        }
        String setNumber = catalog.getSetNumber() == null ? "" : catalog.getSetNumber().trim();
        String name = catalog.getName() == null ? "" : catalog.getName().trim();
        return (setNumber + " " + name).trim();
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
            MarketListing listing,
            String detail
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
                when,
                detail
        );
        sendEmail(opportunity, email);
    }

    private void sendEmail(BuyingOpportunity opportunity, NotificationEmail email) {
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

    public String displayUrl(BuyingOpportunity opportunity) {
        if (opportunity == null) {
            return null;
        }
        if (BuyingOpportunity.TYPE_NEW_SALE.equals(opportunity.getType())
                || BuyingOpportunity.TYPE_ORDER_DELIVERED.equals(opportunity.getType())) {
            String path = orderAppPath(opportunity);
            if (path != null) {
                return path;
            }
        }
        return opportunity.getUrl();
    }

    private String orderAppPath(BuyingOpportunity opportunity) {
        String existing = opportunity.getUrl();
        if (existing != null && existing.startsWith("/orders/")) {
            return existing;
        }
        String key = opportunity.getDedupeKey();
        if (key == null || !key.startsWith("SALE:")) {
            return null;
        }
        String[] parts = key.split(":", 4);
        if (parts.length != 4) {
            return null;
        }
        try {
            Platform platform = Platform.valueOf(parts[1]);
            return orders.findByPlatformAndExternalOrderId(platform, parts[2])
                    .map(order -> "/orders/" + order.getId())
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
