package com.thetimelessvault.orders;

final class ShippingProviders {

    private ShippingProviders() {
    }

    static String infer(String trackingNumber, String shippingProvider) {
        if (shippingProvider != null && !shippingProvider.isBlank()) {
            return shippingProvider.trim();
        }
        if (trackingNumber != null && trackingNumber.regionMatches(true, 0, "1Z", 0, 2)) {
            return "UPS";
        }
        return null;
    }
}
