package com.thetimelessvault.shopify;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.DescriptionBackfillService;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ShopifySkuBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ShopifySkuBackfillService.class);

    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final ShopifyClient shopify;

    public ShopifySkuBackfillService(
            InventoryItemRepository items,
            ChannelListingRepository listings,
            ShopifyClient shopify
    ) {
        this.items = items;
        this.listings = listings;
        this.shopify = shopify;
    }

    public Report run(boolean dryRun) {
        List<InventoryItem> inventory = items.findAllWithCatalog();
        List<Row> rows = new ArrayList<>();
        int candidates = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        for (InventoryItem item : inventory) {
            Optional<ChannelListing> listing = linked(item);
            if (listing.isEmpty()) {
                continue;
            }
            candidates += 1;
            try {
                Row row = preview(item, listing.get());
                if ("UPDATE".equals(row.action()) && !dryRun) {
                    shopify.updateProductSku(listing.get().getExternalId(), handleFrom(listing.get()), item.getSku());
                }
                rows.add(row);
                switch (row.action()) {
                    case "UPDATE" -> updated += 1;
                    case "SKIP" -> skipped += 1;
                    default -> failed += 1;
                }
            } catch (RuntimeException e) {
                failed += 1;
                log.warn("Shopify SKU backfill failed for {}: {}", item.getSku(), e.getMessage());
                rows.add(row("FAIL", item, listing.get(), null, e.getMessage()));
            }
        }
        return new Report(dryRun, candidates, updated, skipped, failed, rows);
    }

    private Row preview(InventoryItem item, ChannelListing listing) {
        if (item.getSku() == null || item.getSku().isBlank()) {
            return row("SKIP", item, listing, null, "Inventory SKU is blank");
        }
        String current = shopify.productSku(listing.getExternalId(), handleFrom(listing));
        if (sameSku(current, item.getSku())) {
            return row("SKIP", item, listing, current, "Shopify already " + item.getSku());
        }
        return row("UPDATE", item, listing, current, label(current) + " -> " + item.getSku());
    }

    private Optional<ChannelListing> linked(InventoryItem item) {
        return listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)
                .filter(listing -> listing.getExternalId() != null && !listing.getExternalId().isBlank()
                        || listing.getLiveUrl() != null && !listing.getLiveUrl().isBlank());
    }

    private static String handleFrom(ChannelListing listing) {
        return DescriptionBackfillService.handleFrom(listing.getLiveUrl());
    }

    private static boolean sameSku(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equalsIgnoreCase(right);
    }

    private static String label(String sku) {
        return sku == null || sku.isBlank() ? "blank" : sku;
    }

    private static Row row(String action, InventoryItem item, ChannelListing listing, String shopifySku, String message) {
        String setNumber = item.getCatalogItem() == null ? null : item.getCatalogItem().getSetNumber();
        return new Row(
                action,
                item.getSku(),
                setNumber,
                shopifySku,
                listing.getShopifyStatus(),
                listing.getLiveUrl(),
                message
        );
    }

    public record Report(
            boolean dryRun,
            int candidates,
            int updated,
            int skipped,
            int failed,
            List<Row> rows
    ) {
    }

    public record Row(
            String action,
            String sku,
            String setNumber,
            String shopifySku,
            String shopifyStatus,
            String liveUrl,
            String message
    ) {
    }
}
