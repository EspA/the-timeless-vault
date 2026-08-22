package com.thetimelessvault.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.bricklink.BrickLinkPublisher;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.shopify.ShopifyClient;
import com.thetimelessvault.shopify.ShopifyProducts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class ListingLinkService {

    private final InventoryService inventoryService;
    private final ChannelListingRepository listings;
    private final EbayClient ebayClient;
    private final BrickLinkClient brickLinkClient;
    private final ShopifyClient shopifyClient;

    public ListingLinkService(
            InventoryService inventoryService,
            ChannelListingRepository listings,
            EbayClient ebayClient,
            BrickLinkClient brickLinkClient,
            ShopifyClient shopifyClient
    ) {
        this.inventoryService = inventoryService;
        this.listings = listings;
        this.ebayClient = ebayClient;
        this.brickLinkClient = brickLinkClient;
        this.shopifyClient = shopifyClient;
    }

    @Transactional
    public ChannelListing link(UUID itemId, Platform platform, String reference, boolean replaceExisting) {
        if (platform == null || platform == Platform.LOCAL) {
            throw ApiException.badRequest("Choose eBay, BrickLink, or Shopify");
        }
        InventoryItem item = inventoryService.get(itemId);
        ResolvedListing resolved = resolve(platform, reference, item);
        Optional<ChannelListing> already = findExisting(platform, resolved);
        if (already.isPresent()) {
            ChannelListing current = already.get();
            if (current.getInventoryItem().getId().equals(itemId)) {
                return current;
            }
            if (!replaceExisting) {
                throw ApiException.conflict(
                        "This " + platformName(platform) + " listing is already linked to "
                                + current.getInventoryItem().getSku() + ". Link it here instead?"
                );
            }
            listings.delete(current);
        }
        ChannelListing local = listings.findByInventoryItemIdAndPlatform(itemId, platform).orElse(null);
        if (local != null && hasLinkedListing(local) && !sameListing(local, resolved) && !replaceExisting) {
            throw ApiException.conflict(
                    "This item already has a " + platformName(platform) + " listing. Replace it with this one?"
            );
        }
        if (local == null) {
            local = ChannelListing.create(item, platform);
        }
        local.markPublished(resolved.externalId(), resolved.liveUrl(), resolved.price());
        switch (platform) {
            case SHOPIFY -> local.setShopifyStatus(resolved.status());
            case BRICKLINK -> local.setBricklinkStatus(resolved.status());
            case EBAY -> local.setEbayStatus(resolved.status());
            case LOCAL -> {
            }
        }
        return listings.save(local);
    }

    private ResolvedListing resolve(Platform platform, String reference, InventoryItem item) {
        ListingReference parsed = ListingReferenceParser.parse(platform, reference);
        return switch (platform) {
            case EBAY -> resolveEbay(parsed, item);
            case BRICKLINK -> resolveBrickLink(parsed);
            case SHOPIFY -> resolveShopify(parsed);
            case LOCAL -> throw ApiException.badRequest("Local listings cannot be linked");
        };
    }

    private ResolvedListing resolveEbay(ListingReference parsed, InventoryItem item) {
        String listingId = parsed.externalId();
        String offerId = null;
        try {
            offerId = ebayClient.findOfferId(item.getSku());
        } catch (RuntimeException ignored) {
            // Classic Seller Hub listings often have no Inventory API offer.
        }
        String externalId = offerId == null || offerId.isBlank() ? listingId : offerId;
        return new ResolvedListing(externalId, parsed.liveUrl(), "ACTIVE", null);
    }

    private ResolvedListing resolveBrickLink(ListingReference parsed) {
        JsonNode lot;
        try {
            lot = brickLinkClient.getInventory(parsed.externalId());
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw ApiException.badRequest("That BrickLink lot was not found");
            }
            throw e;
        }
        boolean stockRoom = lot.path("is_stock_room").asBoolean(false);
        return new ResolvedListing(
                parsed.externalId(),
                BrickLinkPublisher.listingUrl(parsed.externalId()),
                stockRoom ? "UNLISTED" : "ACTIVE",
                decimal(lot, "unit_price")
        );
    }

    private ResolvedListing resolveShopify(ListingReference parsed) {
        JsonNode product = shopifyClient.lookupProduct(parsed.externalId(), parsed.handle());
        String productId = ShopifyProducts.productGid(product.path("id").asText(null));
        if (productId == null) {
            throw ApiException.badRequest("Shopify did not return a product id");
        }
        String handle = text(product, "handle");
        String liveUrl = text(product, "onlineStoreUrl");
        if (liveUrl == null) {
            liveUrl = parsed.liveUrl() != null ? parsed.liveUrl() : ShopifyClient.listingUrl(handle);
        }
        String status = "ACTIVE".equalsIgnoreCase(text(product, "status")) ? "ACTIVE" : "UNLISTED";
        return new ResolvedListing(productId, liveUrl, status, null);
    }

    private Optional<ChannelListing> findExisting(Platform platform, ResolvedListing resolved) {
        Optional<ChannelListing> byId = listings.findByPlatformAndExternalId(platform, resolved.externalId());
        if (byId.isPresent()) {
            return byId;
        }
        if (platform == Platform.SHOPIFY) {
            String numeric = ShopifyProducts.numericId(resolved.externalId());
            if (numeric != null && !numeric.equals(resolved.externalId())) {
                Optional<ChannelListing> byNumeric = listings.findByPlatformAndExternalId(platform, numeric);
                if (byNumeric.isPresent()) {
                    return byNumeric;
                }
            }
        }
        if (resolved.liveUrl() != null) {
            return listings.findByPlatformAndLiveUrl(platform, resolved.liveUrl());
        }
        return Optional.empty();
    }

    private static boolean sameListing(ChannelListing listing, ResolvedListing resolved) {
        if (listing.getExternalId() != null && listing.getExternalId().equals(resolved.externalId())) {
            return true;
        }
        return listing.getLiveUrl() != null && listing.getLiveUrl().equals(resolved.liveUrl());
    }

    private static boolean hasLinkedListing(ChannelListing listing) {
        return listing.getExternalId() != null && !listing.getExternalId().isBlank()
                || listing.getLiveUrl() != null && !listing.getLiveUrl().isBlank();
    }

    private static String platformName(Platform platform) {
        return switch (platform) {
            case SHOPIFY -> "Shopify";
            case BRICKLINK -> "BrickLink";
            case EBAY -> "eBay";
            case LOCAL -> "Local";
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ResolvedListing(String externalId, String liveUrl, String status, BigDecimal price) {
    }
}
