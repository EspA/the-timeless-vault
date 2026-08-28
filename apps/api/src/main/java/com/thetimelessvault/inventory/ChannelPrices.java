package com.thetimelessvault.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ChannelPrices {

    private static final BigDecimal EBAY = new BigDecimal("1.45");
    private static final BigDecimal BRICKLINK = new BigDecimal("1.40");
    private static final BigDecimal BRICKOWL = new BigDecimal("1.40");
    private static final BigDecimal SHOPIFY = new BigDecimal("1.32");

    private ChannelPrices() {
    }

    public static BigDecimal ebay(BigDecimal cost) {
        return markedUp(cost, EBAY);
    }

    public static BigDecimal bricklink(BigDecimal cost) {
        return markedUp(cost, BRICKLINK);
    }

    public static BigDecimal brickowl(BigDecimal cost) {
        return markedUp(cost, BRICKOWL);
    }

    public static BigDecimal shopify(BigDecimal cost) {
        return markedUp(cost, SHOPIFY);
    }

    public static BigDecimal minimumOffer(BigDecimal ebayPrice) {
        if (ebayPrice == null || ebayPrice.signum() <= 0) {
            return null;
        }
        return ebayPrice.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal markedUp(BigDecimal cost, BigDecimal markup) {
        if (cost == null || cost.signum() <= 0) {
            return new BigDecimal("0.01");
        }
        return cost.multiply(markup).setScale(2, RoundingMode.HALF_UP);
    }
}
