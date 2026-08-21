package com.thetimelessvault.market;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarketStatsTest {

    @Test
    void medianOfEmptyIsNull() {
        assertNull(MarketStats.median(List.of()));
    }

    @Test
    void medianOfOddCountIsMiddleValue() {
        assertEquals(new BigDecimal("20.00"), MarketStats.median(List.of(
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("40")
        )));
    }

    @Test
    void medianOfEvenCountAveragesTheMiddlePair() {
        assertEquals(new BigDecimal("25.00"), MarketStats.median(List.of(
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("30"),
                new BigDecimal("40")
        )));
    }

    @Test
    void combinedMedianWeightsByListingCount() {
        CatalogItem catalog = CatalogItem.create("75192-1");
        MarketSnapshot ebay = snapshot(catalog, Platform.EBAY, "100.00", 3);
        MarketSnapshot bricklink = snapshot(catalog, Platform.BRICKLINK, "40.00", 1);
        assertEquals(new BigDecimal("85.00"), MarketStats.combinedMedian(List.of(ebay, bricklink)));
    }

    @Test
    void combinedMedianFallsBackToUnweightedAverage() {
        CatalogItem catalog = CatalogItem.create("75017-1");
        MarketSnapshot ebay = snapshot(catalog, Platform.EBAY, "80.00", 0);
        MarketSnapshot bricklink = snapshot(catalog, Platform.BRICKLINK, "40.00", 0);
        assertEquals(new BigDecimal("60.00"), MarketStats.combinedMedian(List.of(ebay, bricklink)));
    }

    @Test
    void combinedMediansMapByCatalog() {
        CatalogItem falcon = CatalogItem.create("75192-1");
        CatalogItem duel = CatalogItem.create("75017-1");
        Map<UUID, BigDecimal> medians = MarketStats.combinedMedians(List.of(
                snapshot(falcon, Platform.EBAY, "100.00", 1),
                snapshot(duel, Platform.BRICKLINK, "50.00", 2)
        ));
        assertEquals(new BigDecimal("100.00"), medians.get(falcon.getId()));
        assertEquals(new BigDecimal("50.00"), medians.get(duel.getId()));
    }

    private static MarketSnapshot snapshot(CatalogItem catalog, Platform platform, String median, int count) {
        MarketSnapshot snapshot = MarketSnapshot.create(catalog, platform, "NEW", ScanTrigger.MANUAL);
        snapshot.setMedianPrice(new BigDecimal(median));
        snapshot.setListingCount(count);
        return snapshot;
    }
}
