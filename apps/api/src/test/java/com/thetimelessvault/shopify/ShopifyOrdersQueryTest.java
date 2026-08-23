package com.thetimelessvault.shopify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopifyOrdersQueryTest {

    @Test
    void usesUpdatedAtForStatusChanges() {
        assertTrue(ShopifyClient.ORDERS_QUERY.contains("sortKey: UPDATED_AT"));
        assertTrue(ShopifyClient.ORDERS_QUERY.contains("displayFulfillmentStatus"));
        assertTrue(ShopifyClient.ORDERS_QUERY.contains("trackingInfo"));
    }
}
