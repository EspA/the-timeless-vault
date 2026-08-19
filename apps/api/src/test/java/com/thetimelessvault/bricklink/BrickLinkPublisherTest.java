package com.thetimelessvault.bricklink;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrickLinkPublisherTest {

    @Test
    void listingUrlUsesInventoryDetailPage() {
        assertEquals(
                "https://www.bricklink.com/v2/inventory_detail.page?invID=555589441",
                BrickLinkPublisher.listingUrl("555589441")
        );
        assertNull(BrickLinkPublisher.listingUrl(null));
        assertNull(BrickLinkPublisher.listingUrl(" "));
    }
}
