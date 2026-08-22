package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class BrickLinkInventories {

    private BrickLinkInventories() {
    }

    static List<BrickLinkActiveListing> parse(JsonNode data) {
        List<BrickLinkActiveListing> listings = new ArrayList<>();
        if (data == null || data.isNull() || data.isMissingNode()) {
            return listings;
        }
        if (data.isArray()) {
            for (JsonNode node : data) {
                BrickLinkActiveListing listing = parseOne(node);
                if (listing != null) {
                    listings.add(listing);
                }
            }
            return listings;
        }
        BrickLinkActiveListing listing = parseOne(data);
        if (listing != null) {
            listings.add(listing);
        }
        return listings;
    }

    private static BrickLinkActiveListing parseOne(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = text(node.path("item"), "type");
        if (type != null && !"SET".equalsIgnoreCase(type)) {
            return null;
        }
        String inventoryId = firstText(node, "inventory_id");
        String setNumber = text(node.path("item"), "no");
        if (inventoryId == null || setNumber == null) {
            return null;
        }
        return new BrickLinkActiveListing(
                inventoryId,
                setNumber,
                text(node.path("item"), "name"),
                node.path("quantity").asInt(1),
                decimal(node, "unit_price"),
                firstText(node, "new_or_used"),
                firstText(node, "completeness"),
                firstText(node, "remarks")
        );
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
