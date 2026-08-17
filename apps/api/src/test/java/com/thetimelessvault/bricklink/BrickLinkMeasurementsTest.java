package com.thetimelessvault.bricklink;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrickLinkMeasurementsTest {

    @Test
    void convertsGramsToPoundsAndOunces() {
        assertArrayEquals(new int[]{1, 0}, BrickLinkMeasurements.poundsAndOunces(new BigDecimal("453.6")));
        assertArrayEquals(new int[]{0, 4}, BrickLinkMeasurements.poundsAndOunces(new BigDecimal("113.4")));
        assertArrayEquals(new int[]{0, 0}, BrickLinkMeasurements.poundsAndOunces(BigDecimal.ZERO));
    }

    @Test
    void parsesPublicCatalogHtml() {
        String html = """
                Weight: <span id="item-weight-info">6811g</span>
                Item Dim.: <span id="dimSec">58.7 x 50 x 21 cm</span>
                """;
        var parsed = BrickLinkMeasurements.parsePublicCatalog(html);
        assertEquals(new BigDecimal("6811"), parsed.grams());
        assertEquals(new BigDecimal("58.7"), parsed.lengthCm());
        assertEquals(new BigDecimal("50"), parsed.widthCm());
        assertEquals(new BigDecimal("21"), parsed.heightCm());
    }

    @Test
    void convertsCentimetersToInches() {
        assertEquals(new BigDecimal("1.0"), BrickLinkMeasurements.cmToInches(new BigDecimal("2.54")));
        assertEquals(new BigDecimal("13.9"), BrickLinkMeasurements.cmToInches(new BigDecimal("35.40")));
        assertNull(BrickLinkMeasurements.cmToInches(BigDecimal.ZERO));
    }

    @Test
    void addsThreeInchesAndRoundsUp() {
        assertEquals(new BigDecimal("22"), BrickLinkMeasurements.shippingInches(new BigDecimal("18.8")));
        assertEquals(new BigDecimal("21"), BrickLinkMeasurements.shippingInches(new BigDecimal("18.0")));
    }

    @Test
    void addsThirtyPercentWeightAndRoundsUp() {
        assertEquals(7, BrickLinkMeasurements.shippingPounds(5, 0));
        assertEquals(20, BrickLinkMeasurements.shippingPounds(15, 0));
        assertEquals(6, BrickLinkMeasurements.shippingPounds(3, 14));
    }
}
