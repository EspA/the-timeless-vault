package com.thetimelessvault.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemConditionTest {

    @Test
    void mapsBrickLinkCodes() {
        assertEquals("N", ItemCondition.NEW_SEALED.brickLinkNewOrUsed());
        assertEquals("S", ItemCondition.NEW_SEALED.brickLinkCompleteness());
        assertEquals("B", ItemCondition.NEW_INCOMPLETE.brickLinkCompleteness());
        assertEquals("U", ItemCondition.USED_COMPLETE.brickLinkNewOrUsed());
        assertEquals("C", ItemCondition.USED_COMPLETE.brickLinkCompleteness());
    }

    @Test
    void mapsFromBrickLinkCodes() {
        assertEquals(ItemCondition.NEW_SEALED, ItemCondition.fromBrickLink("N", "S"));
        assertEquals(ItemCondition.NEW_COMPLETE, ItemCondition.fromBrickLink("N", "C"));
        assertEquals(ItemCondition.NEW_INCOMPLETE, ItemCondition.fromBrickLink("N", "B"));
        assertEquals(ItemCondition.USED_COMPLETE, ItemCondition.fromBrickLink("U", "C"));
        assertEquals(ItemCondition.USED_INCOMPLETE, ItemCondition.fromBrickLink("U", "B"));
        assertEquals(ItemCondition.NEW_SEALED, ItemCondition.fromBrickLink("N", null));
    }

    @Test
    void mapsEbayConditions() {
        assertEquals("NEW", ItemCondition.NEW_SEALED.ebayCondition());
        assertEquals("NEW_OTHER", ItemCondition.NEW_OTHER.ebayCondition());
        assertEquals("USED_EXCELLENT", ItemCondition.USED_COMPLETE.ebayCondition());
    }
}
