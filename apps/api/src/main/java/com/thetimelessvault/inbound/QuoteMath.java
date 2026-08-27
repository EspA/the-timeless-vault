package com.thetimelessvault.inbound;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class QuoteMath {

    private static final BigDecimal BAND_LOW = new BigDecimal("25");
    private static final BigDecimal BAND_HIGH = new BigDecimal("35");

    private QuoteMath() {
    }

    static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static List<BigDecimal> proratedShipping(List<BigDecimal> costs, BigDecimal shipping) {
        BigDecimal ship = money(shipping);
        int count = costs == null ? 0 : costs.size();
        List<BigDecimal> result = new ArrayList<>(count);
        if (count == 0) {
            return result;
        }
        BigDecimal totalCost = BigDecimal.ZERO;
        for (BigDecimal cost : costs) {
            totalCost = totalCost.add(positive(cost));
        }
        if (totalCost.signum() <= 0 || ship.signum() <= 0) {
            for (int i = 0; i < count; i++) {
                result.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            return result;
        }
        for (BigDecimal raw : costs) {
            BigDecimal cost = positive(raw);
            if (cost.signum() <= 0) {
                result.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            result.add(cost.multiply(ship).divide(totalCost, 2, RoundingMode.HALF_UP));
        }
        return result;
    }

    static BigDecimal marginDollars(BigDecimal median, BigDecimal landed) {
        if (median == null || landed == null) {
            return null;
        }
        return money(median).subtract(money(landed));
    }

    static BigDecimal marginPercent(BigDecimal marginDollars, BigDecimal landed) {
        if (marginDollars == null || landed == null || landed.signum() <= 0) {
            return null;
        }
        return marginDollars
                .multiply(new BigDecimal("100"))
                .divide(money(landed), 0, RoundingMode.HALF_UP);
    }

    static String marginTone(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        if (percent.compareTo(BAND_LOW) < 0) {
            return "low";
        }
        if (percent.compareTo(BAND_HIGH) <= 0) {
            return "mid";
        }
        return "high";
    }

    private static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
