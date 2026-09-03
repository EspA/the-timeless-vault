package com.thetimelessvault.orders;

public record ShipmentTracking(String trackingNumber, String shippingProvider) {
    public static ShipmentTracking of(String trackingNumber, String shippingProvider) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }
        String number = trackingNumber.trim();
        return new ShipmentTracking(number, ShippingProviders.inferOrDefault(number, shippingProvider));
    }
}
