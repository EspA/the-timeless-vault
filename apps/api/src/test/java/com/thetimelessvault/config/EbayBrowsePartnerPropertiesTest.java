package com.thetimelessvault.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayBrowsePartnerPropertiesTest {

    @Test
    void partnerTokenSelectsWaitSeeBuyHostAndMarksBrowseReady() {
        AppProperties.Ebay ebay = new AppProperties().getEbay();
        ebay.getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");

        assertTrue(ebay.partnerBrowseConfigured());
        assertTrue(ebay.browseConfigured());
        assertEquals("https://watchseebuy.com", ebay.browseApiHost());
        assertEquals("partner-browse-token-32-chars-min", ebay.partnerBrowseToken());
    }

    @Test
    void partnerHostOverrideStripsTrailingSlash() {
        AppProperties.Ebay ebay = new AppProperties().getEbay();
        ebay.getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");
        ebay.getWaitseebuy().setBrowseHost("https://watchseebuy.com/");

        assertEquals("https://watchseebuy.com", ebay.browseApiHost());
    }

    @Test
    void blankPartnerHostFallsBackToWaitSeeBuy() {
        AppProperties.Ebay ebay = new AppProperties().getEbay();
        ebay.getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");
        ebay.getWaitseebuy().setBrowseHost("  ");

        assertEquals("https://watchseebuy.com", ebay.browseApiHost());
    }

    @Test
    void withoutPartnerTokenKeepsEbayBrowseHostAndRequiresClientCredentials() {
        AppProperties.Ebay ebay = new AppProperties().getEbay();
        ebay.setBrowseClientId("browse-id");
        ebay.setBrowseClientSecret("browse-secret");

        assertFalse(ebay.partnerBrowseConfigured());
        assertTrue(ebay.browseConfigured());
        assertEquals("https://api.ebay.com", ebay.browseApiHost());
    }

    @Test
    void withoutPartnerTokenFallsBackToSellClientCredentials() {
        AppProperties.Ebay ebay = new AppProperties().getEbay();
        ebay.setClientId("sell-id");
        ebay.setClientSecret("sell-secret");

        assertTrue(ebay.browseConfigured());
        assertEquals("https://api.ebay.com", ebay.browseApiHost());
    }

    @Test
    void browseNotConfiguredWithoutPartnerTokenOrClientCredentials() {
        AppProperties.Ebay ebay = new AppProperties().getEbay();

        assertFalse(ebay.partnerBrowseConfigured());
        assertFalse(ebay.browseConfigured());
        assertEquals("https://api.ebay.com", ebay.browseApiHost());
    }
}
