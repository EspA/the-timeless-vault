package com.thetimelessvault.watch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetWatchPriceFilterTest {

    @Test
    void acceptsAnyPriceWhenRangeIsEmpty() {
        SetWatch watch = new SetWatch();
        assertTrue(watch.acceptsPrice(new BigDecimal("12.50")));
        assertTrue(watch.acceptsPrice(null));
    }

    @Test
    void filtersOutsideInclusiveRange() {
        SetWatch watch = new SetWatch();
        watch.setPriceRange(new BigDecimal("100"), new BigDecimal("200"));
        assertFalse(watch.acceptsPrice(new BigDecimal("99.99")));
        assertTrue(watch.acceptsPrice(new BigDecimal("100")));
        assertTrue(watch.acceptsPrice(new BigDecimal("200")));
        assertFalse(watch.acceptsPrice(new BigDecimal("200.01")));
        assertFalse(watch.acceptsPrice(null));
    }

    @Test
    void swapsInvertedBounds() {
        SetWatch watch = new SetWatch();
        watch.setPriceRange(new BigDecimal("200"), new BigDecimal("100"));
        assertEquals(new BigDecimal("100"), watch.getMinPrice());
        assertEquals(new BigDecimal("200"), watch.getMaxPrice());
    }
}
