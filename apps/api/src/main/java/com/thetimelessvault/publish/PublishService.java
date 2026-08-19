package com.thetimelessvault.publish;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ListingStatus;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayCatalogPreview;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.ebay.EbayPublisher;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.shopify.ShopifyClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PublishService {

    private final InventoryService inventoryService;
    private final ChannelListingRepository listings;
    private final PublishJobRepository jobs;
    private final PublishWorker worker;
    private final ShopifyClient shopifyClient;
    private final BrickLinkClient brickLinkClient;
    private final EbayClient ebayClient;
    private final EbayPublisher ebayPublisher;
    private final PriceGuardRepository priceGuards;
    private final ListingLogService listingLogs;

    public PublishService(
            InventoryService inventoryService,
            ChannelListingRepository listings,
            PublishJobRepository jobs,
            PublishWorker worker,
            ShopifyClient shopifyClient,
            BrickLinkClient brickLinkClient,
            EbayClient ebayClient,
            EbayPublisher ebayPublisher,
            PriceGuardRepository priceGuards,
            ListingLogService listingLogs
    ) {
        this.inventoryService = inventoryService;
        this.listings = listings;
        this.jobs = jobs;
        this.worker = worker;
        this.shopifyClient = shopifyClient;
        this.brickLinkClient = brickLinkClient;
        this.ebayClient = ebayClient;
        this.ebayPublisher = ebayPublisher;
        this.priceGuards = priceGuards;
        this.listingLogs = listingLogs;
    }

    public EbayCatalogPreview ebayCatalogPreview(UUID itemId) {
        return ebayClient.previewCatalog(inventoryService.get(itemId));
    }

    @Transactional
    public List<PublishJob> enqueue(UUID itemId, Set<Platform> platforms) {
        return enqueueJobs(itemId, platforms, false, ListingAction.CREATE);
    }

    @Transactional
    public List<PublishJob> enqueueRetry(UUID itemId, Platform platform) {
        return enqueueJobs(itemId, Set.of(platform), true, ListingAction.CREATE);
    }

    @Transactional
    public List<PublishJob> enqueueUpdate(UUID itemId, Set<Platform> platforms) {
        return enqueueJobs(itemId, platforms, false, ListingAction.UPDATE);
    }

    private List<PublishJob> enqueueJobs(UUID itemId, Set<Platform> platforms, boolean retry, ListingAction action) {
        InventoryItem item = inventoryService.get(itemId);
        Set<Platform> selected = platforms == null || platforms.isEmpty()
                ? EnumSet.allOf(Platform.class)
                : platforms;
        if (action == ListingAction.UPDATE) {
            List<ChannelListing> targets = listings.findByInventoryItemId(itemId).stream()
                    .filter(listing -> selected.contains(listing.getPlatform()))
                    .filter(PublishService::canUpdate)
                    .toList();
            if (targets.isEmpty()) {
                throw ApiException.badRequest(noListingsToUpdateMessage(selected));
            }
            return targets.stream().map(listing -> queueJob(item, listing, ListingAction.UPDATE)).toList();
        }
        if (!retry) {
            List<String> existing = selected.stream()
                    .sorted()
                    .filter(platform -> listings.findByInventoryItemIdAndPlatform(itemId, platform).isPresent())
                    .map(PublishService::platformLabel)
                    .toList();
            if (!existing.isEmpty()) {
                throw ApiException.conflict(alreadyCreatedMessage(existing));
            }
        }
        return selected.stream().map(platform -> {
            ChannelListing listing = listings.findByInventoryItemIdAndPlatform(itemId, platform)
                    .orElseGet(() -> listings.save(ChannelListing.create(item, platform)));
            return queueJob(item, listing, ListingAction.CREATE);
        }).toList();
    }

    private PublishJob queueJob(InventoryItem item, ChannelListing listing, ListingAction action) {
        listing.markPublishing();
        listings.save(listing);
        return jobs.save(PublishJob.queued(item, listing.getPlatform(), action));
    }

    static boolean canUpdate(ChannelListing listing) {
        return listing.getStatus() == ListingStatus.PUBLISHED
                && listing.getExternalId() != null
                && !listing.getExternalId().isBlank();
    }

    static String platformLabel(Platform platform) {
        return switch (platform) {
            case SHOPIFY -> "Shopify";
            case BRICKLINK -> "BrickLink";
            case EBAY -> "eBay";
        };
    }

    static String alreadyCreatedMessage(List<String> channels) {
        if (channels.size() == 1) {
            return "A " + channels.getFirst() + " listing has already been created for this item.";
        }
        return "Listings have already been created for " + joinAnd(channels) + ".";
    }

    static String noListingsToUpdateMessage(Set<Platform> selected) {
        if (selected.size() >= Platform.values().length) {
            return "No existing listings to update. Create a listing first.";
        }
        return "None of the selected channels have an existing listing to update.";
    }

    private static String joinAnd(List<String> parts) {
        if (parts.size() == 2) {
            return parts.get(0) + " and " + parts.get(1);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + ", and " + parts.getLast();
    }

    @Async
    public void runJobs(List<UUID> jobIds) {
        jobIds.forEach(worker::run);
    }

    public List<ChannelListing> listingsFor(UUID itemId) {
        return listings.findByInventoryItemId(itemId);
    }

    public List<PublishJob> jobsFor(UUID itemId) {
        return jobs.findByInventoryItemIdOrderByCreatedAtDesc(itemId);
    }

    @Transactional
    public ChannelListing setShopifyStatus(UUID itemId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ACTIVE") && !normalized.equals("UNLISTED")) {
            throw ApiException.badRequest("Shopify status must be Unlisted or Active");
        }
        InventoryItem item = inventoryService.get(itemId);
        ListingAction action = ListingAction.fromVisibility(normalized);
        try {
            ChannelListing listing = listings.findByInventoryItemIdAndPlatform(itemId, Platform.SHOPIFY)
                    .orElseThrow(() -> ApiException.notFound("Shopify listing not found"));
            if (listing.getStatus() != ListingStatus.PUBLISHED
                    || listing.getExternalId() == null
                    || listing.getExternalId().isBlank()) {
                throw ApiException.badRequest("Publish to Shopify first");
            }
            listing.setShopifyStatus(shopifyClient.updateProductStatus(listing.getExternalId(), normalized));
            ChannelListing saved = listings.save(listing);
            listingLogs.record(item, Platform.SHOPIFY, action, ListingLogStatus.SUCCESS, visibilityNote(normalized));
            return saved;
        } catch (RuntimeException e) {
            listingLogs.record(item, Platform.SHOPIFY, action, ListingLogStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public ChannelListing setBricklinkStatus(UUID itemId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ACTIVE") && !normalized.equals("UNLISTED")) {
            throw ApiException.badRequest("BrickLink status must be Unlisted or Active");
        }
        InventoryItem item = inventoryService.get(itemId);
        ListingAction action = ListingAction.fromVisibility(normalized);
        try {
            ChannelListing listing = listings.findByInventoryItemIdAndPlatform(itemId, Platform.BRICKLINK)
                    .orElseThrow(() -> ApiException.notFound("BrickLink listing not found"));
            if (listing.getStatus() != ListingStatus.PUBLISHED
                    || listing.getExternalId() == null
                    || listing.getExternalId().isBlank()) {
                throw ApiException.badRequest("Publish to BrickLink first");
            }
            boolean inStockRoom = normalized.equals("UNLISTED");
            boolean stockRoom = brickLinkClient.updateStockRoom(listing.getExternalId(), inStockRoom);
            listing.setBricklinkStatus(stockRoom ? "UNLISTED" : "ACTIVE");
            ChannelListing saved = listings.save(listing);
            listingLogs.record(item, Platform.BRICKLINK, action, ListingLogStatus.SUCCESS, visibilityNote(normalized));
            return saved;
        } catch (RuntimeException e) {
            listingLogs.record(item, Platform.BRICKLINK, action, ListingLogStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public ChannelListing setEbayStatus(UUID itemId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ACTIVE") && !normalized.equals("UNLISTED")) {
            throw ApiException.badRequest("eBay status must be Unlisted or Active");
        }
        InventoryItem item = inventoryService.get(itemId);
        ListingAction action = ListingAction.fromVisibility(normalized);
        try {
            ChannelListing listing = listings.findByInventoryItemIdAndPlatform(itemId, Platform.EBAY)
                    .orElseThrow(() -> ApiException.notFound("eBay listing not found"));
            if (listing.getStatus() != ListingStatus.PUBLISHED
                    || listing.getExternalId() == null
                    || listing.getExternalId().isBlank()) {
                throw ApiException.badRequest("Publish to eBay first");
            }
            String current = listing.getEbayStatus();
            if (current == null && listing.getLiveUrl() != null && !listing.getLiveUrl().isBlank()) {
                current = "ACTIVE";
            }
            if (normalized.equals(current)) {
                listing.setEbayStatus(normalized);
                ChannelListing saved = listings.save(listing);
                listingLogs.record(item, Platform.EBAY, action, ListingLogStatus.SUCCESS, visibilityNote(normalized));
                return saved;
            }
            String offerId = resolveEbayOfferId(listing, itemId);
            if (normalized.equals("ACTIVE")) {
                ebayPublisher.syncInventory(item);
                offerId = resolveEbayOfferId(listing, itemId);
                String listingId = ebayClient.publishOffer(offerId);
                listing.setLiveUrl(EbayClient.listingUrl(listingId));
                listing.setEbayStatus("ACTIVE");
            } else {
                ebayClient.withdrawOffer(offerId);
                listing.setEbayStatus("UNLISTED");
            }
            listing.setExternalId(offerId);
            ChannelListing saved = listings.save(listing);
            listingLogs.record(item, Platform.EBAY, action, ListingLogStatus.SUCCESS, visibilityNote(normalized));
            return saved;
        } catch (RuntimeException e) {
            listingLogs.record(item, Platform.EBAY, action, ListingLogStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteShopifyListing(UUID itemId) {
        InventoryItem item = inventoryService.get(itemId);
        try {
            ChannelListing listing = requireInactiveListing(itemId, Platform.SHOPIFY, "shopifyStatus");
            if (listing.getExternalId() != null && !listing.getExternalId().isBlank()) {
                shopifyClient.deleteProduct(listing.getExternalId());
            }
            removeListing(listing);
            listingLogs.record(item, Platform.SHOPIFY, ListingAction.DELETE, ListingLogStatus.SUCCESS, "Listing deleted");
        } catch (RuntimeException e) {
            listingLogs.record(item, Platform.SHOPIFY, ListingAction.DELETE, ListingLogStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteBricklinkListing(UUID itemId) {
        InventoryItem item = inventoryService.get(itemId);
        try {
            ChannelListing listing = requireInactiveListing(itemId, Platform.BRICKLINK, "bricklinkStatus");
            if (listing.getExternalId() != null && !listing.getExternalId().isBlank()) {
                brickLinkClient.deleteInventory(listing.getExternalId());
            }
            removeListing(listing);
            listingLogs.record(item, Platform.BRICKLINK, ListingAction.DELETE, ListingLogStatus.SUCCESS, "Listing deleted");
        } catch (RuntimeException e) {
            listingLogs.record(item, Platform.BRICKLINK, ListingAction.DELETE, ListingLogStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteEbayListing(UUID itemId) {
        InventoryItem item = inventoryService.get(itemId);
        try {
            ChannelListing listing = requireInactiveListing(itemId, Platform.EBAY, "ebayStatus");
            ebayClient.purgeSku(item.getSku());
            removeListing(listing);
            listingLogs.record(item, Platform.EBAY, ListingAction.DELETE, ListingLogStatus.SUCCESS, "Listing deleted");
        } catch (RuntimeException e) {
            listingLogs.record(item, Platform.EBAY, ListingAction.DELETE, ListingLogStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private static String visibilityNote(String status) {
        return "UNLISTED".equals(status) ? "Listing is now unlisted" : "Listing is now active";
    }

    private ChannelListing requireInactiveListing(UUID itemId, Platform platform, String statusField) {
        String name = switch (platform) {
            case SHOPIFY -> "Shopify";
            case BRICKLINK -> "BrickLink";
            case EBAY -> "eBay";
        };
        ChannelListing listing = listings.findByInventoryItemIdAndPlatform(itemId, platform)
                .orElseThrow(() -> ApiException.notFound(name + " listing not found"));
        String status = switch (statusField) {
            case "shopifyStatus" -> listing.getShopifyStatus();
            case "bricklinkStatus" -> listing.getBricklinkStatus();
            default -> listing.getEbayStatus();
        };
        if (!"UNLISTED".equalsIgnoreCase(status)) {
            throw ApiException.badRequest("Make the " + name + " listing inactive before deleting it.");
        }
        return listing;
    }

    private void removeListing(ChannelListing listing) {
        priceGuards.findByChannelListingId(listing.getId()).ifPresent(priceGuards::delete);
        listings.delete(listing);
    }

    private String resolveEbayOfferId(ChannelListing listing, UUID itemId) {
        String externalId = listing.getExternalId();
        String liveUrl = listing.getLiveUrl();
        boolean looksLikeListingId = liveUrl != null && externalId != null && liveUrl.contains("/itm/" + externalId);
        if (!looksLikeListingId) {
            return externalId;
        }
        String sku = inventoryService.get(itemId).getSku();
        String offerId = ebayClient.findOfferId(sku);
        if (offerId == null || offerId.isBlank()) {
            throw ApiException.badRequest("Could not find the eBay offer for this item. Publish to eBay again.");
        }
        return offerId;
    }
}
