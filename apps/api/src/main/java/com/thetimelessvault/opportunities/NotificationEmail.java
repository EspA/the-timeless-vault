package com.thetimelessvault.opportunities;

public record NotificationEmail(
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
        java.time.Instant scannedAt,
        String detail
) {
    boolean buyingOpportunity() {
        return BuyingOpportunity.TYPE_BUYING_OPPORTUNITY.equals(type);
    }

    boolean newSale() {
        return BuyingOpportunity.TYPE_NEW_SALE.equals(type);
    }

    boolean priceGuard() {
        return BuyingOpportunity.TYPE_PRICE_HIGH.equals(type)
                || BuyingOpportunity.TYPE_PRICE_LOW.equals(type);
    }

    boolean scanFailed() {
        return BuyingOpportunity.TYPE_SCAN_FAILED.equals(type);
    }

    String typeLabel() {
        if (type == null || type.isBlank()) {
            return "NOTIFICATION";
        }
        return type.replace('_', ' ');
    }
}
