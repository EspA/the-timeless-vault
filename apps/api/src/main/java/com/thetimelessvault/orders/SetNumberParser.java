package com.thetimelessvault.orders;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SetNumberParser {

    private static final Pattern TTV_SKU = Pattern.compile("^TTV-(.+)-([A-Za-z0-9]{4})$");
    private static final Pattern LEGO_SET = Pattern.compile("(?i)\\blego\\s+(\\d{4,6}(?:-\\d+)?)\\b");
    private static final Pattern BARE_SET = Pattern.compile("\\b(\\d{4,6}(?:-\\d+)?)\\b");

    private SetNumberParser() {
    }

    public static String fromSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        Matcher matcher = TTV_SKU.matcher(sku.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    public static String fromTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        Matcher lego = LEGO_SET.matcher(title);
        if (lego.find()) {
            return lego.group(1);
        }
        Matcher bare = BARE_SET.matcher(title);
        return bare.find() ? bare.group(1) : null;
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
