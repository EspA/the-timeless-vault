package com.thetimelessvault.ebay;

import java.math.BigDecimal;
import java.util.List;

public record EbayActiveListing(
        String listingId,
        String sku,
        String title,
        String description,
        BigDecimal price,
        int quantity,
        List<String> imageUrls,
        String conditionId,
        String conditionName
) {
}
