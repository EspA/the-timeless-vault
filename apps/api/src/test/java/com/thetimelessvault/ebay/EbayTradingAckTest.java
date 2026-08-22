package com.thetimelessvault.ebay;

import com.thetimelessvault.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayTradingAckTest {

    @Test
    void listingIdFromUrlReadsItemId() {
        assertEquals("365847291012", EbayClient.listingIdFromUrl("https://www.ebay.com/itm/365847291012"));
        assertEquals("365847291012", EbayClient.listingIdFromUrl("https://www.ebay.com/itm/365847291012?hash=item"));
        assertNull(EbayClient.listingIdFromUrl("https://www.ebay.com/sch/i.html"));
        assertNull(EbayClient.listingIdFromUrl(null));
    }

    @Test
    void treatsAlreadyEndedListingAsSuccess() {
        String xml = """
                <EndItemResponse>
                  <Ack>Failure</Ack>
                  <Errors>
                    <ShortMessage>The auction has already been closed.</ShortMessage>
                    <ErrorCode>1047</ErrorCode>
                  </Errors>
                </EndItemResponse>
                """;
        assertTrue(EbayClient.alreadyEnded(xml));
        assertDoesNotThrow(() -> EbayClient.assertTradingAck(xml, "EndItem"));
    }

    @Test
    void surfacesOtherTradingFailures() {
        String xml = """
                <EndItemResponse>
                  <Ack>Failure</Ack>
                  <Errors>
                    <LongMessage>You are not allowed to end this item.</LongMessage>
                    <ErrorCode>17</ErrorCode>
                  </Errors>
                </EndItemResponse>
                """;
        assertFalse(EbayClient.alreadyEnded(xml));
        ApiException error = assertThrows(ApiException.class, () -> EbayClient.assertTradingAck(xml, "EndItem"));
        assertTrue(error.getMessage().contains("You are not allowed to end this item."));
    }
}
