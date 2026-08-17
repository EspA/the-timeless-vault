package com.thetimelessvault.ebay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EbayBrowseContextTest {

    @Test
    void normalizesUsZipAndBuildsShippingHeader() {
        assertEquals("19406", EbayBrowseContext.normalizePostalCode("19406-1234"));
        assertEquals("19406", EbayBrowseContext.normalizePostalCode(" 19406 "));
        assertEquals("", EbayBrowseContext.normalizePostalCode(""));
        assertEquals(
                "contextualLocation=country%3DUS%2Czip%3D19406",
                EbayBrowseContext.endUserContext("19406")
        );
        assertEquals(
                "contextualLocation=country%3DUS",
                EbayBrowseContext.endUserContext(null)
        );
    }
}
