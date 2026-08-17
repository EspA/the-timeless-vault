package com.thetimelessvault.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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
}
