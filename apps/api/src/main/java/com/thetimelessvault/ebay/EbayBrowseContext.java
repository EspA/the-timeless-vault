package com.thetimelessvault.ebay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class EbayBrowseContext {

    public static final String BUYER_POSTAL_CODE_KEY = "ebay.buyer_postal_code";

    private EbayBrowseContext() {
    }

    public static String normalizePostalCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String compact = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (compact.matches("\\d{5,}")) {
            return compact.substring(0, 5);
        }
        return compact.length() > 10 ? compact.substring(0, 10) : compact;
    }

    public static String endUserContext(String postalCode) {
        String zip = normalizePostalCode(postalCode);
        String location = zip.isBlank() ? "country=US" : "country=US,zip=" + zip;
        return "contextualLocation=" + URLEncoder.encode(location, StandardCharsets.UTF_8);
    }
}
