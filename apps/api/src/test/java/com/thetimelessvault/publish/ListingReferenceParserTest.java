package com.thetimelessvault.publish;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListingReferenceParserTest {

    @Test
    void readsEbayItemUrlsAndIds() {
        assertEquals("227311449843", ListingReferenceParser.parse(Platform.EBAY, "https://www.ebay.com/itm/227311449843").externalId());
        assertEquals("227311449843", ListingReferenceParser.parse(Platform.EBAY, "227311449843").externalId());
        assertEquals(
                "https://www.ebay.com/itm/227311449843",
                ListingReferenceParser.parse(Platform.EBAY, "https://www.ebay.com/itm/227311449843?hash=abc").liveUrl()
        );
    }

    @Test
    void readsBrickLinkInventoryUrlsAndIds() {
        ListingReference parsed = ListingReferenceParser.parse(
                Platform.BRICKLINK,
                "https://www.bricklink.com/v2/inventory_detail.page?invID=525841562"
        );
        assertEquals("525841562", parsed.externalId());
        assertEquals("https://www.bricklink.com/v2/inventory_detail.page?invID=525841562", parsed.liveUrl());
        assertEquals("525841562", ListingReferenceParser.parse(Platform.BRICKLINK, "525841562").externalId());
    }

    @Test
    void readsBrickOwlLotUrlsAndIds() {
        ListingReference parsed = ListingReferenceParser.parse(
                Platform.BRICKOWL,
                "https://www.brickowl.com/inventory/778899"
        );
        assertEquals("778899", parsed.externalId());
        assertEquals("https://www.brickowl.com/inventory/778899", parsed.liveUrl());
        assertEquals("778899", ListingReferenceParser.parse(Platform.BRICKOWL, "778899").externalId());
        assertEquals(
                "445566",
                ListingReferenceParser.parse(Platform.BRICKOWL, "https://www.brickowl.com/store/the-timeless-vault?lot_id=445566").externalId()
        );
    }

    @Test
    void readsShopifyGidsHandlesAndUrls() {
        assertEquals(
                "gid://shopify/Product/123",
                ListingReferenceParser.parse(Platform.SHOPIFY, "gid://shopify/Product/123").externalId()
        );
        assertEquals(
                "cruiser",
                ListingReferenceParser.parse(Platform.SHOPIFY, "https://thetimelessvault.com/products/cruiser").handle()
        );
        assertEquals(
                "gid://shopify/Product/123",
                ListingReferenceParser.parse(Platform.SHOPIFY, "123").externalId()
        );
    }

    @Test
    void rejectsBlankValues() {
        assertThrows(ApiException.class, () -> ListingReferenceParser.parse(Platform.EBAY, " "));
    }
}
