package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.sales.SetNumberParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ShopifyProducts {

    private ShopifyProducts() {
    }

    static List<ShopifyActiveListing> parseAdmin(JsonNode nodes) {
        return parse(nodes, true);
    }

    static List<ShopifyActiveListing> parseStorefront(JsonNode nodes) {
        return parse(nodes, false);
    }

    private static List<ShopifyActiveListing> parse(JsonNode nodes, boolean admin) {
        List<ShopifyActiveListing> listings = new ArrayList<>();
        if (nodes == null || !nodes.isArray()) {
            return listings;
        }
        for (JsonNode node : nodes) {
            ShopifyActiveListing listing = parseOne(node, admin);
            if (listing != null && listing.quantity() == 1) {
                listings.add(listing);
            }
        }
        return listings;
    }

    private static ShopifyActiveListing parseOne(JsonNode node, boolean admin) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String productId = productGid(text(node, "id"));
        if (productId == null) {
            return null;
        }
        String handle = text(node, "handle");
        String title = text(node, "title");
        JsonNode variants = admin ? node.path("variants").path("nodes") : node.path("variants");
        JsonNode variant = firstVariant(variants);
        String sku = variant == null ? null : firstText(variant, "sku");
        String liveUrl = firstText(node, "onlineStoreUrl");
        if (liveUrl == null) {
            liveUrl = ShopifyClient.listingUrl(handle);
        }
        Integer quantity = quantity(variant, admin);
        if (quantity == null || quantity != 1) {
            return null;
        }
        return new ShopifyActiveListing(
                productId,
                handle,
                title,
                sku,
                SetNumberParser.firstNonBlank(SetNumberParser.fromSku(sku), SetNumberParser.fromTitle(title)),
                quantity,
                variant == null ? null : decimal(variant, "price"),
                liveUrl
        );
    }

    public static String productGid(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String trimmed = id.trim();
        return trimmed.startsWith("gid://") ? trimmed : "gid://shopify/Product/" + trimmed;
    }

    public static String numericId(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        int slash = productId.lastIndexOf('/');
        return slash >= 0 ? productId.substring(slash + 1) : productId;
    }

    private static Integer quantity(JsonNode variant, boolean admin) {
        if (variant == null) {
            return null;
        }
        JsonNode value = admin ? variant.get("inventoryQuantity") : variant.get("inventory_quantity");
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        if (value.isInt() || value.isLong() || value.canConvertToInt()) {
            return value.asInt();
        }
        try {
            return Integer.parseInt(value.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonNode firstVariant(JsonNode variants) {
        if (variants == null || !variants.isArray() || variants.isEmpty()) {
            return null;
        }
        for (JsonNode variant : variants) {
            if (variant != null && variant.isObject()) {
                return variant;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String field) {
        return text(node, field);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
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
}
