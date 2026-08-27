package com.thetimelessvault.inbound;

import java.util.Locale;

public enum ShippingCarrier {
    UPS,
    USPS,
    DHL,
    FEDEX,
    COLISSIMO,
    POSTNL;

    public boolean trackable() {
        return this == UPS || this == USPS || this == FEDEX;
    }

    public static ShippingCarrier fromProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        String value = provider.trim().toUpperCase(Locale.ROOT);
        if (value.contains("USPS") || value.contains("POSTAL SERVICE") || value.equals("US POSTAL")) {
            return USPS;
        }
        if (value.contains("FEDEX") || value.contains("FED EX") || value.contains("FEDERAL EXPRESS")) {
            return FEDEX;
        }
        if (value.equals("UPS") || value.startsWith("UPS ") || value.contains("UNITED PARCEL")) {
            return UPS;
        }
        return null;
    }

    public static ShippingCarrier fromTrackingNumber(String trackingNumber) {
        if (trackingNumber != null && trackingNumber.regionMatches(true, 0, "1Z", 0, 2)) {
            return UPS;
        }
        return null;
    }

    public static ShippingCarrier resolve(String provider, String trackingNumber) {
        ShippingCarrier fromProvider = fromProvider(provider);
        return fromProvider != null ? fromProvider : fromTrackingNumber(trackingNumber);
    }
}
