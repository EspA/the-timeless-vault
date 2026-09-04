package com.thetimelessvault.alerts;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.opportunities.BuyingOpportunity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListingAdjustmentPricingTest {

    @Test
    void highUsesCostPlusEbayMargin() {
        assertEquals(
                0,
                new BigDecimal("145.00").compareTo(ListingAdjustmentPricing.recommended(
                        BuyingOpportunity.TYPE_PRICE_HIGH,
                        Platform.EBAY,
                        new BigDecimal("100.00"),
                        new BigDecimal("180.00")
                ))
        );
    }

    @Test
    void lowUsesMarketWhenAboveFloor() {
        assertEquals(
                0,
                new BigDecimal("180.00").compareTo(ListingAdjustmentPricing.recommended(
                        BuyingOpportunity.TYPE_PRICE_LOW,
                        Platform.BRICKLINK,
                        new BigDecimal("100.00"),
                        new BigDecimal("180.00")
                ))
        );
    }

    @Test
    void lowUsesFloorWhenMarketIsBelowMargin() {
        assertEquals(
                0,
                new BigDecimal("140.00").compareTo(ListingAdjustmentPricing.recommended(
                        BuyingOpportunity.TYPE_PRICE_LOW,
                        Platform.BRICKLINK,
                        new BigDecimal("100.00"),
                        new BigDecimal("110.00")
                ))
        );
    }

    @Test
    void shopifyFloorIsThirtyTwoPercent() {
        assertEquals(
                0,
                new BigDecimal("132.00").compareTo(ListingAdjustmentPricing.recommended(
                        BuyingOpportunity.TYPE_PRICE_HIGH,
                        Platform.SHOPIFY,
                        new BigDecimal("100.00"),
                        new BigDecimal("200.00")
                ))
        );
    }

    @Test
    void missingCostFallsBackToMarket() {
        assertEquals(
                0,
                new BigDecimal("180.00").compareTo(ListingAdjustmentPricing.recommended(
                        BuyingOpportunity.TYPE_PRICE_HIGH,
                        Platform.EBAY,
                        null,
                        new BigDecimal("180")
                ))
        );
        assertNull(ListingAdjustmentPricing.recommended(
                BuyingOpportunity.TYPE_PRICE_HIGH, Platform.EBAY, null, null));
    }
}
