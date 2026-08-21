package com.thetimelessvault.ebay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EbayAccountDeletionChallengeTest {

    @Test
    void buildsDeterministicChallengeResponse() {
        String first = EbayAccountDeletionChallenge.response(
                "abc123",
                "token456",
                "https://admin.thetimelessvault.com/webhooks/ebay/account-deletion"
        );
        String second = EbayAccountDeletionChallenge.response(
                "abc123",
                "token456",
                "https://admin.thetimelessvault.com/webhooks/ebay/account-deletion"
        );
        assertEquals(first, second);
        assertEquals(64, first.length());
    }
}
