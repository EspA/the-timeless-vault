package com.thetimelessvault.inventory;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;

import java.math.BigDecimal;

public final class ChannelPrice {

    private ChannelPrice() {
    }

    public static BigDecimal amount(InventoryItem item, Platform platform) {
        BigDecimal value = item.priceFor(platform);
        if (value == null) {
            throw ApiException.badRequest("Set a " + label(platform) + " before creating this listing");
        }
        return value;
    }

    public static String required(InventoryItem item, Platform platform) {
        return amount(item, platform).toPlainString();
    }

    private static String label(Platform platform) {
        return switch (platform) {
            case EBAY -> "eBay price";
            case BRICKLINK -> "BrickLink price";
            case SHOPIFY -> "Shopify price";
            case LOCAL -> "price";
        };
    }
}
