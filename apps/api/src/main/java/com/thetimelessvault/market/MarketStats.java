package com.thetimelessvault.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class MarketStats {

    private MarketStats() {
    }

    static BigDecimal median(List<BigDecimal> prices) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(prices);
        sorted.sort(Comparator.naturalOrder());
        int count = sorted.size();
        if (count % 2 == 1) {
            return sorted.get(count / 2).setScale(2, RoundingMode.HALF_UP);
        }
        return sorted.get(count / 2 - 1)
                .add(sorted.get(count / 2))
                .divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
    }
}
