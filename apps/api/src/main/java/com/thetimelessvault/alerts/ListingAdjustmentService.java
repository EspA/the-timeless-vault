package com.thetimelessvault.alerts;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.publish.ChannelListing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ListingAdjustmentService {

    private final ListingAdjustmentRepository adjustments;
    private final BuyingOpportunityService opportunities;

    public ListingAdjustmentService(
            ListingAdjustmentRepository adjustments,
            BuyingOpportunityService opportunities
    ) {
        this.adjustments = adjustments;
        this.opportunities = opportunities;
    }

    public List<ListingAdjustment> listActive() {
        return adjustments.findActiveWithListing().stream()
                .filter(row -> row.getChannelListing() == null || row.getChannelListing().shouldEvaluatePriceGuard())
                .toList();
    }

    @Transactional
    public ListingAdjustmentView dismiss(UUID id) {
        ListingAdjustment row = adjustments.findByIdWithListing(id)
                .orElseThrow(() -> ApiException.notFound("Listing adjustment not found"));
        if (row.isActive()) {
            row.dismiss(Instant.now());
            adjustments.save(row);
        }
        return view(row);
    }

    public ListingAdjustmentView requireActive(UUID id) {
        return view(activeRow(id));
    }

    @Transactional
    public ListingAdjustmentView resolve(UUID id) {
        ListingAdjustment row = activeRow(id);
        row.resolve(Instant.now());
        adjustments.save(row);
        return view(row);
    }

    private ListingAdjustment activeRow(UUID id) {
        ListingAdjustment row = adjustments.findByIdWithListing(id)
                .orElseThrow(() -> ApiException.notFound("Listing adjustment not found"));
        if (!row.isActive()) {
            throw ApiException.badRequest("This listing adjustment is no longer active");
        }
        return row;
    }

    @Transactional
    public void recordOutOfRange(
            ChannelListing listing,
            String type,
            BigDecimal yours,
            BigDecimal market,
            BigDecimal highPercent,
            BigDecimal lowPercent,
            Instant scannedAt
    ) {
        recordOutOfRange(listing, type, yours, market, highPercent, lowPercent, scannedAt, Instant.now());
    }

    void recordOutOfRange(
            ChannelListing listing,
            String type,
            BigDecimal yours,
            BigDecimal market,
            BigDecimal highPercent,
            BigDecimal lowPercent,
            Instant scannedAt,
            Instant now
    ) {
        Instant detectedAt = scannedAt == null ? now : scannedAt;
        ListingAdjustment row = listing.getId() == null
                ? null
                : adjustments.findByChannelListingId(listing.getId()).orElse(null);
        if (row == null) {
            notifyAndSave(ListingAdjustment.create(listing, type, yours, market, detectedAt),
                    listing, type, yours, market, highPercent, lowPercent, detectedAt);
            return;
        }
        if (row.isActive() && type.equals(row.getType())) {
            row.refresh(yours, market, now);
            adjustments.save(row);
            return;
        }
        if (row.isActive()) {
            row.activate(type, yours, market, detectedAt);
            notifyAndSave(row, listing, type, yours, market, highPercent, lowPercent, detectedAt);
            return;
        }
        if (row.isDismissed() && type.equals(row.getType()) && !row.dismissedOnPriorDay(now)) {
            row.refresh(yours, market, now);
            adjustments.save(row);
            return;
        }
        boolean notify = row.isResolved() || !type.equals(row.getType());
        row.activate(type, yours, market, detectedAt);
        if (notify) {
            notifyAndSave(row, listing, type, yours, market, highPercent, lowPercent, detectedAt);
            return;
        }
        adjustments.save(row);
    }

    @Transactional
    public void resolveInRange(ChannelListing listing) {
        resolveInRange(listing, Instant.now());
    }

    void resolveInRange(ChannelListing listing, Instant now) {
        if (listing.getId() == null) {
            return;
        }
        adjustments.findByChannelListingId(listing.getId()).ifPresent(row -> {
            if (!row.isResolved()) {
                row.resolve(now);
                adjustments.save(row);
            }
        });
    }

    private void notifyAndSave(
            ListingAdjustment row,
            ChannelListing listing,
            String type,
            BigDecimal yours,
            BigDecimal market,
            BigDecimal highPercent,
            BigDecimal lowPercent,
            Instant scannedAt
    ) {
        adjustments.save(row);
        opportunities.recordPriceGuard(listing, type, yours, market, highPercent, lowPercent, scannedAt);
    }

    public record ListingAdjustmentView(
            UUID id,
            Instant when,
            String type,
            Platform platform,
            String listingUrl,
            String listingStatus,
            UUID inventoryItemId,
            String inventoryLabel,
            BigDecimal cost,
            BigDecimal currentListingPrice,
            BigDecimal marketPrice,
            BigDecimal recommendedPrice
    ) {
        static ListingAdjustmentView from(ListingAdjustment row) {
            ChannelListing listing = row.getChannelListing();
            InventoryItem item = listing == null ? null : listing.getInventoryItem();
            CatalogItem catalog = item == null ? null : item.getCatalogItem();
            Platform platform = listing == null ? null : listing.getPlatform();
            BigDecimal cost = item == null ? null : item.getCost();
            BigDecimal current = publishedOrChannelPrice(listing, item);
            BigDecimal market = row.getMarketPrice();
            return new ListingAdjustmentView(
                    row.getId(),
                    row.getDetectedAt(),
                    row.getType(),
                    platform,
                    listing == null ? null : BuyingOpportunityService.listingUrl(listing),
                    listing == null ? null : statusLabel(listing),
                    item == null ? null : item.getId(),
                    itemLabel(item, catalog),
                    cost,
                    current,
                    market,
                    ListingAdjustmentPricing.recommended(row.getType(), platform, cost, market)
            );
        }
    }

    private static BigDecimal publishedOrChannelPrice(ChannelListing listing, InventoryItem item) {
        if (listing != null && listing.getLastPublishedPrice() != null) {
            return listing.getLastPublishedPrice();
        }
        return item == null || listing == null ? null : item.priceFor(listing.getPlatform());
    }

    public ListingAdjustmentView view(ListingAdjustment row) {
        return ListingAdjustmentView.from(row);
    }

    static String statusLabel(ChannelListing listing) {
        String visibility = listing.visibilityStatus();
        if (visibility != null && !visibility.isBlank()) {
            return visibility;
        }
        return listing.getStatus() == null ? null : listing.getStatus().name();
    }

    private static String itemLabel(InventoryItem item, CatalogItem catalog) {
        String heading = BuyingOpportunityService.catalogHeading(catalog);
        if (!heading.isBlank()) {
            return heading;
        }
        if (item != null && item.getTitle() != null && !item.getTitle().isBlank()) {
            return item.getTitle();
        }
        return item == null || item.getSku() == null ? "Open item" : item.getSku();
    }
}
