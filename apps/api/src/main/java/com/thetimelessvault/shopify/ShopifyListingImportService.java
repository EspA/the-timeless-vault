package com.thetimelessvault.shopify;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ShopifyListingImportService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyListingImportService.class);

    private final ShopifyClient shopify;
    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final TransactionTemplate transactions;

    public ShopifyListingImportService(
            ShopifyClient shopify,
            InventoryItemRepository items,
            ChannelListingRepository listings,
            PlatformTransactionManager transactionManager
    ) {
        this.shopify = shopify;
        this.items = items;
        this.listings = listings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Report run(boolean dryRun) {
        if (!shopify.configured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Connect Shopify in Settings before importing listings");
        }
        List<ShopifyActiveListing> active = shopify.listActiveProducts();
        List<InventoryItem> inStock = items.findWithCatalogByStockStatus(StockStatus.IN_STOCK);
        Set<String> claimedProducts = new HashSet<>();
        Set<UUID> claimedItems = new HashSet<>();
        claimAlreadyLinked(active, inStock, claimedProducts, claimedItems);

        List<Row> rows = new ArrayList<>();
        int skipped = 0;
        int linked = 0;
        int unmatched = 0;
        int failed = 0;
        for (ShopifyActiveListing listing : active) {
            try {
                if (claimedProducts.contains(listing.productId())) {
                    InventoryItem item = alreadyLinkedItem(listing).orElse(null);
                    rows.add(row("SKIP", listing, item, "Already in inventory"));
                    skipped += 1;
                    continue;
                }
                Optional<InventoryItem> match = matchItem(listing, inStock, claimedItems);
                if (match.isEmpty()) {
                    rows.add(row("UNMATCHED", listing, null, "No in-stock item without a Shopify listing"));
                    unmatched += 1;
                    continue;
                }
                InventoryItem item = match.get();
                claimedProducts.add(listing.productId());
                claimedItems.add(item.getId());
                if (!dryRun) {
                    transactions.execute(status -> {
                        attachListing(item, listing);
                        return null;
                    });
                }
                rows.add(row("LINK", listing, item, matchReason(listing, item)));
                linked += 1;
            } catch (RuntimeException e) {
                failed += 1;
                log.warn("Shopify listing import failed for {}: {}", listing.productId(), e.getMessage());
                rows.add(row("FAIL", listing, null, e.getMessage()));
            }
        }
        return new Report(dryRun, active.size(), skipped, linked, unmatched, failed, rows);
    }

    private void claimAlreadyLinked(
            List<ShopifyActiveListing> active,
            List<InventoryItem> inStock,
            Set<String> claimedProducts,
            Set<UUID> claimedItems
    ) {
        for (ShopifyActiveListing listing : active) {
            alreadyLinkedItem(listing).ifPresent(item -> {
                claimedProducts.add(listing.productId());
                claimedItems.add(item.getId());
            });
        }
        for (InventoryItem item : inStock) {
            listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)
                    .filter(ShopifyListingImportService::hasLinkedListing)
                    .ifPresent(listing -> claimedItems.add(item.getId()));
        }
    }

    private Optional<InventoryItem> alreadyLinkedItem(ShopifyActiveListing listing) {
        Optional<ChannelListing> byGid = listings.findByPlatformAndExternalId(Platform.SHOPIFY, listing.productId());
        if (byGid.isPresent()) {
            return Optional.of(byGid.get().getInventoryItem());
        }
        String numericId = ShopifyProducts.numericId(listing.productId());
        if (numericId != null && !numericId.equals(listing.productId())) {
            Optional<ChannelListing> byNumeric = listings.findByPlatformAndExternalId(Platform.SHOPIFY, numericId);
            if (byNumeric.isPresent()) {
                return Optional.of(byNumeric.get().getInventoryItem());
            }
        }
        if (listing.liveUrl() != null) {
            Optional<ChannelListing> byUrl = listings.findByPlatformAndLiveUrl(Platform.SHOPIFY, listing.liveUrl());
            if (byUrl.isPresent()) {
                return Optional.of(byUrl.get().getInventoryItem());
            }
        }
        return Optional.empty();
    }

    private Optional<InventoryItem> matchItem(
            ShopifyActiveListing listing,
            List<InventoryItem> inStock,
            Set<UUID> claimedItems
    ) {
        if (listing.sku() != null) {
            Optional<InventoryItem> bySku = inStock.stream()
                    .filter(item -> !claimedItems.contains(item.getId()))
                    .filter(item -> item.getSku().equalsIgnoreCase(listing.sku()))
                    .findFirst();
            if (bySku.isPresent()) {
                return bySku;
            }
        }
        if (listing.setNumber() == null) {
            return Optional.empty();
        }
        List<InventoryItem> candidates = inStock.stream()
                .filter(item -> !claimedItems.contains(item.getId()))
                .filter(item -> sameSet(item.getCatalogItem().getSetNumber(), listing.setNumber()))
                .sorted(Comparator.comparing(InventoryItem::getCreatedAt))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(item -> item.getQuantity() == listing.quantity())
                .findFirst()
                .or(() -> Optional.of(candidates.getFirst()));
    }

    private void attachListing(InventoryItem item, ShopifyActiveListing listing) {
        ChannelListing channel = listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)
                .orElseGet(() -> ChannelListing.create(item, Platform.SHOPIFY));
        if (hasLinkedListing(channel) && channel.getExternalId() != null
                && !channel.getExternalId().equals(listing.productId())
                && !channel.getExternalId().equals(ShopifyProducts.numericId(listing.productId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Item already has a different Shopify listing");
        }
        BigDecimal price = listing.price();
        channel.markPublished(listing.productId(), listing.liveUrl(), price);
        channel.setShopifyStatus("ACTIVE");
        listings.save(channel);
        if (price != null && (item.getShopifyPrice() == null || item.getShopifyPrice().signum() == 0)) {
            item.setShopifyPrice(price);
            items.save(item);
        }
    }

    private static boolean hasLinkedListing(ChannelListing listing) {
        return listing.getExternalId() != null && !listing.getExternalId().isBlank()
                || listing.getLiveUrl() != null && !listing.getLiveUrl().isBlank();
    }

    static boolean sameSet(String inventorySet, String shopifySet) {
        if (inventorySet == null || shopifySet == null) {
            return false;
        }
        String left = inventorySet.trim();
        String right = shopifySet.trim();
        if (left.equalsIgnoreCase(right)) {
            return true;
        }
        return left.equalsIgnoreCase(right + "-1") || right.equalsIgnoreCase(left + "-1");
    }

    private static String matchReason(ShopifyActiveListing listing, InventoryItem item) {
        if (listing.sku() != null && listing.sku().equalsIgnoreCase(item.getSku())) {
            return "Linked existing SKU";
        }
        return "Linked by set number";
    }

    private static Row row(String action, ShopifyActiveListing listing, InventoryItem item, String message) {
        return new Row(
                action,
                listing.productId(),
                listing.sku(),
                listing.title(),
                listing.setNumber(),
                item == null ? null : item.getSku(),
                message
        );
    }

    public record Report(
            boolean dryRun,
            int fetched,
            int skipped,
            int linked,
            int unmatched,
            int failed,
            List<Row> rows
    ) {
    }

    public record Row(
            String action,
            String listingId,
            String shopifySku,
            String title,
            String setNumber,
            String inventorySku,
            String message
    ) {
    }
}
