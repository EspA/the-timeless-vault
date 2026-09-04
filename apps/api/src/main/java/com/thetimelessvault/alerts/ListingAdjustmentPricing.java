package com.thetimelessvault.alerts;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.ChannelPrices;
import com.thetimelessvault.opportunities.BuyingOpportunity;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class ListingAdjustmentPricing {

    private ListingAdjustmentPricing() {
    }

    static BigDecimal recommended(String type, Platform platform, BigDecimal cost, BigDecimal market) {
        BigDecimal floor = hasCost(cost) ? ChannelPrices.forPlatform(platform, cost) : null;
        BigDecimal median = scaled(market);
        if (BuyingOpportunity.TYPE_PRICE_LOW.equals(type)) {
            if (median != null && (floor == null || median.compareTo(floor) > 0)) {
                return median;
            }
            return floor;
        }
        return floor != null ? floor : median;
    }

    private static boolean hasCost(BigDecimal cost) {
        return cost != null && cost.signum() > 0;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
