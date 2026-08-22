package com.thetimelessvault.bricklink;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ItemCondition;
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
public class BrickLinkListingImportService {

    private static final Logger log = LoggerFactory.getLogger(BrickLinkListingImportService.class);

    private final BrickLinkClient brickLink;
    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final TransactionTemplate transactions;

    public BrickLinkListingImportService(
            BrickLinkClient brickLink,
            InventoryItemRepository items,
            ChannelListingRepository listings,
            PlatformTransactionManager transactionManager
    ) {
        this.brickLink = brickLink;
        this.items = items;
        this.listings = listings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Report run(boolean dryRun) {
        if (!brickLink.configured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Connect BrickLink in Settings before importing listings");
        }
        List<BrickLinkActiveListing> active = brickLink.listActiveSetInventories();
        List<InventoryItem> inStock = items.findWithCatalogByStockStatus(StockStatus.IN_STOCK);
        Set<String> claimedLots = new HashSet<>();
        Set<UUID> claimedItems = new HashSet<>();
        claimAlreadyLinked(active, inStock, claimedLots, claimedItems);

        List<Row> rows = new ArrayList<>();
        int skipped = 0;
        int linked = 0;
        int unmatched = 0;
        int failed = 0;
        for (BrickLinkActiveListing listing : active) {
            try {
                if (claimedLots.contains(listing.inventoryId())) {
                    InventoryItem item = alreadyLinkedItem(listing).orElse(null);
                    rows.add(row("SKIP", listing, item, "Already in inventory"));
                    skipped += 1;
                    continue;
                }
                Optional<InventoryItem> match = matchItem(listing, inStock, claimedItems);
                if (match.isEmpty()) {
                    rows.add(row("UNMATCHED", listing, null, "No in-stock item without a BrickLink listing"));
                    unmatched += 1;
                    continue;
                }
                InventoryItem item = match.get();
                claimedLots.add(listing.inventoryId());
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
                log.warn("BrickLink listing import failed for {}: {}", listing.inventoryId(), e.getMessage());
                rows.add(row("FAIL", listing, null, e.getMessage()));
            }
        }
        return new Report(dryRun, active.size(), skipped, linked, unmatched, failed, rows);
    }

    private void claimAlreadyLinked(
            List<BrickLinkActiveListing> active,
            List<InventoryItem> inStock,
            Set<String> claimedLots,
            Set<UUID> claimedItems
    ) {
        for (BrickLinkActiveListing listing : active) {
            alreadyLinkedItem(listing).ifPresent(item -> {
                claimedLots.add(listing.inventoryId());
                claimedItems.add(item.getId());
            });
        }
        for (InventoryItem item : inStock) {
            listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK)
                    .filter(BrickLinkListingImportService::hasLinkedListing)
                    .ifPresent(listing -> claimedItems.add(item.getId()));
        }
    }

    private Optional<InventoryItem> alreadyLinkedItem(BrickLinkActiveListing listing) {
        Optional<ChannelListing> byId = listings.findByPlatformAndExternalId(Platform.BRICKLINK, listing.inventoryId());
        if (byId.isPresent()) {
            return Optional.of(byId.get().getInventoryItem());
        }
        return listings.findByPlatformAndLiveUrl(Platform.BRICKLINK, BrickLinkPublisher.listingUrl(listing.inventoryId()))
                .map(ChannelListing::getInventoryItem);
    }

    private Optional<InventoryItem> matchItem(
            BrickLinkActiveListing listing,
            List<InventoryItem> inStock,
            Set<UUID> claimedItems
    ) {
        if (listing.remarks() != null) {
            Optional<InventoryItem> bySku = inStock.stream()
                    .filter(item -> !claimedItems.contains(item.getId()))
                    .filter(item -> item.getSku().equalsIgnoreCase(listing.remarks()))
                    .findFirst();
            if (bySku.isPresent()) {
                return bySku;
            }
        }
        ItemCondition condition = ItemCondition.fromBrickLink(listing.newOrUsed(), listing.completeness());
        List<InventoryItem> candidates = inStock.stream()
                .filter(item -> !claimedItems.contains(item.getId()))
                .filter(item -> sameSet(item.getCatalogItem().getSetNumber(), listing.setNumber()))
                .filter(item -> item.getCondition() == condition)
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

    private void attachListing(InventoryItem item, BrickLinkActiveListing listing) {
        ChannelListing channel = listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK)
                .orElseGet(() -> ChannelListing.create(item, Platform.BRICKLINK));
        if (hasLinkedListing(channel) && channel.getExternalId() != null
                && !channel.getExternalId().equals(listing.inventoryId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Item already has a different BrickLink listing");
        }
        BigDecimal price = listing.unitPrice();
        channel.markPublished(listing.inventoryId(), BrickLinkPublisher.listingUrl(listing.inventoryId()), price);
        channel.setBricklinkStatus("ACTIVE");
        listings.save(channel);
        if (price != null && (item.getBricklinkPrice() == null || item.getBricklinkPrice().signum() == 0)) {
            item.setBricklinkPrice(price);
            items.save(item);
        }
    }

    private static boolean hasLinkedListing(ChannelListing listing) {
        return listing.getExternalId() != null && !listing.getExternalId().isBlank()
                || listing.getLiveUrl() != null && !listing.getLiveUrl().isBlank();
    }

    static boolean sameSet(String inventorySet, String brickLinkSet) {
        if (inventorySet == null || brickLinkSet == null) {
            return false;
        }
        String left = inventorySet.trim();
        String right = brickLinkSet.trim();
        if (left.equalsIgnoreCase(right)) {
            return true;
        }
        return left.equalsIgnoreCase(right + "-1") || right.equalsIgnoreCase(left + "-1");
    }

    private static String matchReason(BrickLinkActiveListing listing, InventoryItem item) {
        if (listing.remarks() != null && listing.remarks().equalsIgnoreCase(item.getSku())) {
            return "Linked existing SKU";
        }
        return "Linked by set and condition";
    }

    private static Row row(String action, BrickLinkActiveListing listing, InventoryItem item, String message) {
        return new Row(
                action,
                listing.inventoryId(),
                listing.remarks(),
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
            String remarks,
            String title,
            String setNumber,
            String inventorySku,
            String message
    ) {
    }
}
