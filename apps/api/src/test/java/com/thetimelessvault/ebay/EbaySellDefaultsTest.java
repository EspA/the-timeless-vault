package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EbaySellDefaultsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void picksLocationByNameOrKeyAndSkipsDisabled() throws Exception {
        var items = mapper.readTree("""
                [
                  {"merchantLocationKey":"old","name":"Garage","merchantLocationStatus":"DISABLED"},
                  {"merchantLocationKey":"mailbox","name":"Private Mail Box","merchantLocationStatus":"ENABLED"}
                ]
                """);

        assertEquals("mailbox", EbaySellDefaults.pick(
                items, "Private Mail Box", "merchantLocationKey", "name", "merchantLocationStatus", "ENABLED"));
        assertEquals("mailbox", EbaySellDefaults.pick(
                items, "mailbox", "merchantLocationKey", "name", "merchantLocationStatus", "ENABLED"));
        assertEquals("mailbox", EbaySellDefaults.pick(
                items, "", "merchantLocationKey", "name", "merchantLocationStatus", "ENABLED"));
    }

    @Test
    void picksPolicyByNameOrId() throws Exception {
        var items = mapper.readTree("""
                [
                  {"fulfillmentPolicyId":"111","name":"UPS Standard Shipping policy"},
                  {"fulfillmentPolicyId":"222","name":"Economy"}
                ]
                """);

        assertEquals("111", EbaySellDefaults.pick(
                items, "UPS Standard Shipping policy", "fulfillmentPolicyId", "name", null, null));
        assertEquals("222", EbaySellDefaults.pick(
                items, "222", "fulfillmentPolicyId", "name", null, null));
        assertEquals("111", EbaySellDefaults.pick(
                items, "", "fulfillmentPolicyId", "name", null, null));
    }

    @Test
    void keepsConfiguredValueWhenListIsEmpty() {
        assertEquals("kept", EbaySellDefaults.pick(null, "kept", "id", "name", null, null));
        assertNull(EbaySellDefaults.pick(null, "  ", "id", "name", null, null));
    }

    @Test
    void sanitizesMerchantLocationKey() {
        assertEquals("private-mail-box", EbayClient.merchantLocationKey("Private Mail Box"));
        assertEquals("warehouse", EbayClient.merchantLocationKey("   "));
    }
}
