package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MarketStats {

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

    public static Map<UUID, BigDecimal> combinedMedians(Collection<MarketSnapshot> snapshots) {
        Map<UUID, Map<Platform, MarketSnapshot>> latest = new HashMap<>();
        if (snapshots != null) {
            for (MarketSnapshot snapshot : snapshots) {
                UUID catalogId = snapshot.getCatalogItem().getId();
                latest.computeIfAbsent(catalogId, key -> new EnumMap<>(Platform.class))
                        .merge(snapshot.getPlatform(), snapshot, MarketStats::newer);
            }
        }
        Map<UUID, BigDecimal> medians = new HashMap<>();
        latest.forEach((catalogId, byPlatform) -> {
            BigDecimal combined = combinedMedian(byPlatform.values());
            if (combined != null) {
                medians.put(catalogId, combined);
            }
        });
        return medians;
    }

    static BigDecimal combinedMedian(Collection<MarketSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        BigDecimal weighted = BigDecimal.ZERO;
        int totalCount = 0;
        List<BigDecimal> unweighted = new ArrayList<>();
        for (MarketSnapshot snapshot : snapshots) {
            if (snapshot.getMedianPrice() == null) {
                continue;
            }
            unweighted.add(snapshot.getMedianPrice());
            int count = snapshot.getListingCount() == null ? 0 : snapshot.getListingCount();
            if (count > 0) {
                weighted = weighted.add(snapshot.getMedianPrice().multiply(BigDecimal.valueOf(count)));
                totalCount += count;
            }
        }
        if (totalCount > 0) {
            return weighted.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
        }
        if (unweighted.isEmpty()) {
            return null;
        }
        return unweighted.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(unweighted.size()), 2, RoundingMode.HALF_UP);
    }

    private static MarketSnapshot newer(MarketSnapshot left, MarketSnapshot right) {
        if (left.getScannedAt() == null) {
            return right;
        }
        if (right.getScannedAt() == null || !right.getScannedAt().isAfter(left.getScannedAt())) {
            return left;
        }
        return right;
    }
}
