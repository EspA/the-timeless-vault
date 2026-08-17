package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayListingDetailsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsAuctionWithBestOfferAndFreeShipping() throws Exception {
        var item = mapper.readTree("""
                {
                  "title": "LEGO 10143 Death Star II",
                  "image": { "imageUrl": "https://i.ebayimg.com/images/g/abc/s-l500.jpg" },
                  "buyingOptions": ["AUCTION", "BEST_OFFER"],
                  "currentBidPrice": { "value": "420.00", "currency": "USD" },
                  "seller": { "username": "brickshop", "feedbackScore": 1842, "feedbackPercentage": "99.8" },
                  "shippingOptions": [
                    { "shippingCostType": "FIXED", "shippingCost": { "value": "0.00", "currency": "USD" } }
                  ]
                }
                """);

        EbayListingDetails details = EbayListingDetails.from(item);

        assertEquals("https://i.ebayimg.com/images/g/abc/s-l500.jpg", details.imageUrl());
        assertEquals(1842, details.feedbackScore());
        assertEquals("99.8", details.feedbackPercentage());
        assertTrue(details.auction());
        assertEquals(new BigDecimal("420.00"), details.currentBid());
        assertTrue(details.bestOffer());
        assertEquals(new BigDecimal("0.00"), details.shippingCost());
        assertFalse(details.shippingCalculated());
    }

    @Test
    void fallsBackToThumbnailAndMarksCalculatedShipping() throws Exception {
        var item = mapper.readTree("""
                {
                  "thumbnailImages": [{ "imageUrl": "https://i.ebayimg.com/images/g/xyz/s-l140.jpg" }],
                  "buyingOptions": ["FIXED_PRICE"],
                  "seller": { "feedbackScore": 12, "feedbackPercentage": "100.0" },
                  "shippingOptions": [{ "shippingCostType": "CALCULATED" }]
                }
                """);

        EbayListingDetails details = EbayListingDetails.from(item);

        assertEquals("https://i.ebayimg.com/images/g/xyz/s-l140.jpg", details.imageUrl());
        assertFalse(details.auction());
        assertFalse(details.bestOffer());
        assertNull(details.currentBid());
        assertNull(details.shippingCost());
        assertTrue(details.shippingCalculated());
    }

    @Test
    void usesQuotedCalculatedShippingWhenPresent() throws Exception {
        var item = mapper.readTree("""
                {
                  "shippingOptions": [
                    { "shippingCostType": "CALCULATED", "shippingCost": { "value": "12.45", "currency": "USD" } }
                  ]
                }
                """);
        EbayListingDetails details = EbayListingDetails.from(item);
        assertEquals(new BigDecimal("12.45"), details.shippingCost());
        assertFalse(details.shippingCalculated());
    }
}
