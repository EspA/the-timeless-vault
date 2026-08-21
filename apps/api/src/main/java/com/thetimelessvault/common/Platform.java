package com.thetimelessvault.common;

import java.util.EnumSet;
import java.util.Set;

public enum Platform {
    SHOPIFY,
    BRICKLINK,
    EBAY,
    LOCAL;

    public boolean isListingChannel() {
        return this != LOCAL;
    }

    public static Set<Platform> listingChannels() {
        return EnumSet.of(SHOPIFY, BRICKLINK, EBAY);
    }
}
