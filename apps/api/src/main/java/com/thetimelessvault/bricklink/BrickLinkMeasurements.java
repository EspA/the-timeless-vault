package com.thetimelessvault.bricklink;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BrickLinkMeasurements {

    private static final BigDecimal GRAMS_PER_OUNCE = new BigDecimal("28.349523125");
    private static final BigDecimal CM_PER_INCH = new BigDecimal("2.54");

    private BrickLinkMeasurements() {
    }

    public static int[] poundsAndOunces(BigDecimal grams) {
        if (grams == null || grams.compareTo(BigDecimal.ZERO) <= 0) {
            return new int[]{0, 0};
        }
        int totalOz = grams.divide(GRAMS_PER_OUNCE, 0, RoundingMode.HALF_UP).intValue();
        return new int[]{totalOz / 16, totalOz % 16};
    }

    public static BrickLinkPackageValues parsePublicCatalog(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        java.util.regex.Matcher weight = java.util.regex.Pattern
                .compile("id=\"item-weight-info\">\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        java.util.regex.Matcher dims = java.util.regex.Pattern
                .compile("id=\"dimSec\">\\s*([0-9]+(?:\\.[0-9]+)?)\\s*x\\s*([0-9]+(?:\\.[0-9]+)?)\\s*x\\s*([0-9]+(?:\\.[0-9]+)?)\\s*cm", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        BigDecimal grams = weight.find() ? new BigDecimal(weight.group(1)) : null;
        BigDecimal length = null;
        BigDecimal width = null;
        BigDecimal height = null;
        if (dims.find()) {
            length = new BigDecimal(dims.group(1));
            width = new BigDecimal(dims.group(2));
            height = new BigDecimal(dims.group(3));
        }
        if (grams == null && length == null) {
            return null;
        }
        return new BrickLinkPackageValues(grams, length, width, height);
    }

    public record BrickLinkPackageValues(BigDecimal grams, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
    }

    public static BigDecimal cmToInches(BigDecimal cm) {
        if (cm == null || cm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return cm.divide(CM_PER_INCH, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal shippingInches(BigDecimal inches) {
        if (inches == null) {
            return null;
        }
        return inches.add(new BigDecimal("3")).setScale(0, RoundingMode.CEILING);
    }

    public static int shippingPounds(int lbs, int oz) {
        if (lbs <= 0 && oz <= 0) {
            return 0;
        }
        BigDecimal totalPounds = BigDecimal.valueOf(lbs)
                .add(BigDecimal.valueOf(oz).divide(BigDecimal.valueOf(16), 4, RoundingMode.HALF_UP));
        return totalPounds.multiply(new BigDecimal("1.3")).setScale(0, RoundingMode.CEILING).intValue();
    }
}
