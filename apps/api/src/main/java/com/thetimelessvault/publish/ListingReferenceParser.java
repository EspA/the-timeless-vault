package com.thetimelessvault.publish;

import com.thetimelessvault.bricklink.BrickLinkPublisher;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.shopify.ShopifyClient;
import com.thetimelessvault.shopify.ShopifyProducts;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ListingReferenceParser {

    private static final Pattern EBAY_ITEM = Pattern.compile("(?:/itm/|item=|itemid=)(\\d{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRICKLINK_INV = Pattern.compile("(?:invid=)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOPIFY_GID = Pattern.compile("gid://shopify/Product/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOPIFY_HANDLE = Pattern.compile("/products/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("^\\d{6,}$");

    private ListingReferenceParser() {
    }

    static ListingReference parse(Platform platform, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw ApiException.badRequest("Paste a listing URL or id");
        }
        return switch (platform) {
            case EBAY -> ebay(value);
            case BRICKLINK -> brickLink(value);
            case SHOPIFY -> shopify(value);
            case LOCAL -> throw ApiException.badRequest("Local listings cannot be linked");
        };
    }

    private static ListingReference ebay(String value) {
        Matcher matcher = EBAY_ITEM.matcher(value);
        String listingId = matcher.find() ? matcher.group(1) : DIGITS.matcher(value).matches() ? value : null;
        if (listingId == null) {
            throw ApiException.badRequest("Could not read an eBay item id from that value");
        }
        return new ListingReference(listingId, null, EbayClient.listingUrl(listingId));
    }

    private static ListingReference brickLink(String value) {
        Matcher matcher = BRICKLINK_INV.matcher(value);
        String inventoryId = matcher.find() ? matcher.group(1) : DIGITS.matcher(value).matches() ? value : null;
        if (inventoryId == null) {
            throw ApiException.badRequest("Could not read a BrickLink inventory id from that value");
        }
        return new ListingReference(inventoryId, null, BrickLinkPublisher.listingUrl(inventoryId));
    }

    private static ListingReference shopify(String value) {
        Matcher gid = SHOPIFY_GID.matcher(value);
        if (gid.find()) {
            String productId = ShopifyProducts.productGid(gid.group(1));
            return new ListingReference(productId, null, null);
        }
        Matcher handleMatch = SHOPIFY_HANDLE.matcher(value);
        if (handleMatch.find()) {
            String handle = decode(handleMatch.group(1));
            return new ListingReference(null, handle, ShopifyClient.listingUrl(handle));
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return new ListingReference(ShopifyProducts.productGid(value), null, null);
        }
        if (value.startsWith("http")) {
            throw ApiException.badRequest("Could not read a Shopify product from that URL");
        }
        return new ListingReference(null, value, ShopifyClient.listingUrl(value));
    }

    private static String decode(String handle) {
        try {
            return URI.create("https://local/" + handle).getPath().substring(1);
        } catch (IllegalArgumentException e) {
            return handle.toLowerCase(Locale.ROOT);
        }
    }
}
