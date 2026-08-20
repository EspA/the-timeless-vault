package com.thetimelessvault.opportunities;

public record AlertEmail(
        String type,
        String setNumber,
        String setName,
        String photoUrl,
        String price,
        String percentVsMedian,
        String listingUrl,
        String inventoryUrl,
        String platform,
        String sellerName,
        String sellerMeta,
        java.time.Instant scannedAt
) {
    boolean buyingOpportunity() {
        return BuyingOpportunity.TYPE_BUYING_OPPORTUNITY.equals(type);
    }

    boolean priceGuard() {
        return BuyingOpportunity.TYPE_PRICE_HIGH.equals(type)
                || BuyingOpportunity.TYPE_PRICE_LOW.equals(type);
    }

    String typeLabel() {
        if (type == null || type.isBlank()) {
            return "ALERT";
        }
        return type.replace('_', ' ');
    }
}
