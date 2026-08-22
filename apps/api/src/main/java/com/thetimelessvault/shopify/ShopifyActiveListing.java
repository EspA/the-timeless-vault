package com.thetimelessvault.shopify;

import java.math.BigDecimal;

public record ShopifyActiveListing(
        String productId,
        String handle,
        String title,
        String sku,
        String setNumber,
        int quantity,
        BigDecimal price,
        String liveUrl
) {
}
