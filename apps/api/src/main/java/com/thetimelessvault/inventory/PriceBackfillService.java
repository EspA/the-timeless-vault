package com.thetimelessvault.inventory;

import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.shopify.ShopifyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PriceBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PriceBackfillService.class);

    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final ShopifyClient shopify;
    private final BrickLinkClient brickLink;
    private final TransactionTemplate transactions;

    public PriceBackfillService(
            InventoryItemRepository items,
            ChannelListingRepository listings,
            ShopifyClient shopify,
            BrickLinkClient brickLink,
            PlatformTransactionManager transactionManager
    ) {
        this.items = items;
        this.listings = listings;
        this.shopify = shopify;
        this.brickLink = brickLink;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Report run(boolean dryRun) {
        List<InventoryItem> candidates = items.findAllWithCatalog();
        List<Row> rows = new ArrayList<>();
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        for (InventoryItem item : candidates) {
            try {
                Row row = dryRun ? preview(item) : transactions.execute(status -> apply(item));
                rows.add(row);
                switch (row.action()) {
                    case "UPDATE" -> updated += 1;
                    case "SKIP" -> skipped += 1;
                    default -> failed += 1;
                }
            } catch (RuntimeException e) {
                failed += 1;
                log.warn("Price backfill failed for {}: {}", item.getSku(), e.getMessage());
                rows.add(row("FAIL", item, item.getShopifyPrice(), item.getBricklinkPrice(), e.getMessage()));
            }
        }
        return new Report(dryRun, candidates.size(), updated, skipped, failed, rows);
    }

    private Row preview(InventoryItem item) {
        Draft draft = load(item);
        return row(draft.changed() ? "UPDATE" : "SKIP", item, draft.shopifyPrice, draft.bricklinkPrice, draft.message);
    }

    private Row apply(InventoryItem item) {
        InventoryItem current = items.findWithCatalogById(item.getId()).orElse(item);
        Draft draft = load(current);
        if (!draft.changed()) {
            return row("SKIP", current, draft.shopifyPrice, draft.bricklinkPrice, draft.message);
        }
        if (draft.shopifyChanged) {
            current.setShopifyPrice(draft.shopifyPrice);
            draft.shopifyListing.ifPresent(listing -> {
                listing.markUpdated(listing.getExternalId(), listing.getLiveUrl(), draft.shopifyPrice);
                listings.save(listing);
            });
        }
        if (draft.bricklinkChanged) {
            current.setBricklinkPrice(draft.bricklinkPrice);
            draft.bricklinkListing.ifPresent(listing -> {
                listing.markUpdated(listing.getExternalId(), listing.getLiveUrl(), draft.bricklinkPrice);
                listings.save(listing);
            });
        }
        current.touch();
        items.save(current);
        return row("UPDATE", current, draft.shopifyPrice, draft.bricklinkPrice, draft.message);
    }

    private Draft load(InventoryItem item) {
        Optional<ChannelListing> shopifyListing = linked(item, Platform.SHOPIFY);
        BigDecimal shopifyPrice = null;
        String shopifyNote;
        if (shopifyListing.isEmpty()) {
            shopifyNote = "Shopify listing not linked";
        } else {
            try {
                BigDecimal fetched = positive(shopify.productPrice(
                        shopifyListing.get().getExternalId(),
                        DescriptionBackfillService.handleFrom(shopifyListing.get().getLiveUrl())
                ));
                if (fetched == null) {
                    shopifyNote = "Shopify price was empty";
                } else if (same(item.getShopifyPrice(), fetched)) {
                    shopifyPrice = fetched;
                    shopifyNote = "Shopify already " + fetched.toPlainString();
                } else {
                    shopifyPrice = fetched;
                    shopifyNote = "Shopify " + label(item.getShopifyPrice()) + " -> " + fetched.toPlainString();
                }
            } catch (RuntimeException e) {
                shopifyNote = "Shopify: " + e.getMessage();
            }
        }

        Optional<ChannelListing> brickLinkListing = linked(item, Platform.BRICKLINK)
                .filter(listing -> listing.getExternalId() != null && !listing.getExternalId().isBlank());
        BigDecimal bricklinkPrice = null;
        String bricklinkNote;
        if (brickLinkListing.isEmpty()) {
            bricklinkNote = "BrickLink listing not linked";
        } else {
            try {
                BigDecimal fetched = positive(decimal(
                        brickLink.getInventory(brickLinkListing.get().getExternalId()).path("unit_price").asText("")
                ));
                if (fetched == null) {
                    bricklinkNote = "BrickLink price was empty";
                } else if (same(item.getBricklinkPrice(), fetched)) {
                    bricklinkPrice = fetched;
                    bricklinkNote = "BrickLink already " + fetched.toPlainString();
                } else {
                    bricklinkPrice = fetched;
                    bricklinkNote = "BrickLink " + label(item.getBricklinkPrice()) + " -> " + fetched.toPlainString();
                }
            } catch (RuntimeException e) {
                bricklinkNote = "BrickLink: " + e.getMessage();
            }
        }

        boolean shopifyChanged = shopifyPrice != null && !same(item.getShopifyPrice(), shopifyPrice);
        boolean bricklinkChanged = bricklinkPrice != null && !same(item.getBricklinkPrice(), bricklinkPrice);
        return new Draft(
                shopifyPrice,
                bricklinkPrice,
                shopifyChanged,
                bricklinkChanged,
                shopifyListing,
                brickLinkListing,
                shopifyNote + "; " + bricklinkNote
        );
    }

    private Optional<ChannelListing> linked(InventoryItem item, Platform platform) {
        return listings.findByInventoryItemIdAndPlatform(item.getId(), platform)
                .filter(listing -> listing.getExternalId() != null && !listing.getExternalId().isBlank()
                        || listing.getLiveUrl() != null && !listing.getLiveUrl().isBlank());
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private static BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String label(BigDecimal value) {
        return value == null ? "blank" : value.toPlainString();
    }

    private static Row row(String action, InventoryItem item, BigDecimal shopifyPrice, BigDecimal bricklinkPrice, String message) {
        String setNumber = item.getCatalogItem() == null ? null : item.getCatalogItem().getSetNumber();
        return new Row(action, item.getSku(), setNumber, shopifyPrice, bricklinkPrice, message);
    }

    private record Draft(
            BigDecimal shopifyPrice,
            BigDecimal bricklinkPrice,
            boolean shopifyChanged,
            boolean bricklinkChanged,
            Optional<ChannelListing> shopifyListing,
            Optional<ChannelListing> bricklinkListing,
            String message
    ) {
        boolean changed() {
            return shopifyChanged || bricklinkChanged;
        }
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
            BigDecimal shopifyPrice,
            BigDecimal bricklinkPrice,
            String message
    ) {
    }
}
