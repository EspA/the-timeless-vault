package com.thetimelessvault.orders;

final class ShippingProviders {

    static final String DEFAULT = "UPS";

    private ShippingProviders() {
    }

    static String infer(String trackingNumber, String shippingProvider) {
        if (shippingProvider != null && !shippingProvider.isBlank()) {
            return shippingProvider.trim();
        }
        if (trackingNumber != null && trackingNumber.regionMatches(true, 0, "1Z", 0, 2)) {
            return DEFAULT;
        }
        return null;
    }

    static String inferOrDefault(String trackingNumber, String shippingProvider) {
        return defaulted(infer(trackingNumber, shippingProvider));
    }

    static String defaulted(String shippingProvider) {
        if (shippingProvider == null || shippingProvider.isBlank()) {
            return DEFAULT;
        }
        return shippingProvider;
    }
}
