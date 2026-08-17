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

    public PublishService(
            InventoryService inventoryService,
            ChannelListingRepository listings,
            PublishJobRepository jobs,
            PublishWorker worker,
            ShopifyClient shopifyClient,
            BrickLinkClient brickLinkClient,
            EbayClient ebayClient,
            EbayPublisher ebayPublisher,
            PriceGuardRepository priceGuards
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
    }

    public EbayCatalogPreview ebayCatalogPreview(UUID itemId) {
        return ebayClient.previewCatalog(inventoryService.get(itemId));
    }

    @Transactional
    public List<PublishJob> enqueue(UUID itemId, Set<Platform> platforms) {
        InventoryItem item = inventoryService.get(itemId);
        Set<Platform> selected = platforms == null || platforms.isEmpty()
                ? EnumSet.allOf(Platform.class)
                : platforms;
        return selected.stream().map(platform -> {
            ChannelListing listing = listings.findByInventoryItemIdAndPlatform(itemId, platform)
                    .orElseGet(() -> listings.save(ChannelListing.create(item, platform)));
            listing.markPublishing();
            listings.save(listing);
            return jobs.save(PublishJob.queued(item, platform));
        }).toList();
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
        ChannelListing listing = listings.findByInventoryItemIdAndPlatform(itemId, Platform.SHOPIFY)
                .orElseThrow(() -> ApiException.notFound("Shopify listing not found"));
        if (listing.getStatus() != ListingStatus.PUBLISHED
                || listing.getExternalId() == null
                || listing.getExternalId().isBlank()) {
            throw ApiException.badRequest("Publish to Shopify first");
        }
        listing.setShopifyStatus(shopifyClient.updateProductStatus(listing.getExternalId(), normalized));
        return listings.save(listing);
    }

    @Transactional
    public ChannelListing setBricklinkStatus(UUID itemId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ACTIVE") && !normalized.equals("UNLISTED")) {
            throw ApiException.badRequest("BrickLink status must be Unlisted or Active");
        }
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
        return listings.save(listing);
    }

    @Transactional
    public ChannelListing setEbayStatus(UUID itemId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ACTIVE") && !normalized.equals("UNLISTED")) {
            throw ApiException.badRequest("eBay status must be Unlisted or Active");
        }
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
            return listings.save(listing);
        }
        String offerId = resolveEbayOfferId(listing, itemId);
        if (normalized.equals("ACTIVE")) {
            InventoryItem item = inventoryService.get(itemId);
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
        return listings.save(listing);
    }

    @Transactional
    public void deleteShopifyListing(UUID itemId) {
        ChannelListing listing = requireInactiveListing(itemId, Platform.SHOPIFY, "shopifyStatus");
        if (listing.getExternalId() != null && !listing.getExternalId().isBlank()) {
            shopifyClient.deleteProduct(listing.getExternalId());
        }
        removeListing(listing);
    }

    @Transactional
    public void deleteBricklinkListing(UUID itemId) {
        ChannelListing listing = requireInactiveListing(itemId, Platform.BRICKLINK, "bricklinkStatus");
        if (listing.getExternalId() != null && !listing.getExternalId().isBlank()) {
            brickLinkClient.deleteInventory(listing.getExternalId());
        }
        removeListing(listing);
    }

    @Transactional
    public void deleteEbayListing(UUID itemId) {
        InventoryItem item = inventoryService.get(itemId);
        ChannelListing listing = requireInactiveListing(itemId, Platform.EBAY, "ebayStatus");
        ebayClient.purgeSku(item.getSku());
        removeListing(listing);
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
