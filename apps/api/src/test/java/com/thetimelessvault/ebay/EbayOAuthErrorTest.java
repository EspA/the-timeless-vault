package com.thetimelessvault.ebay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EbayOAuthErrorTest {

    @Test
    void readsEbayOAuthErrorBody() {
        String raw = """
                {"error":"invalid_grant","error_description":"invalid refresh token"}
                """;
        assertEquals("invalid_grant — invalid refresh token", EbayTokenService.oauthErrorMessage(raw, "400"));
    }

    @Test
    void fallsBackWhenBodyIsEmpty() {
        assertEquals("400 BAD_REQUEST", EbayTokenService.oauthErrorMessage(" ", "400 BAD_REQUEST"));
    }

    @Test
    void extractsAuthorizationCodeFromEbaySuccessUrl() {
        String pasted = "https://signin.ebay.com/ws/eBayISAPI.dll?ThirdPartyAuthSucessFailure"
                + "&isAuthSuccessful=true&code=v%5E1.1%23abc&expires_in=299";
        assertEquals("v^1.1#abc", EbayTokenService.authorizationCodeFrom(pasted));
    }

    @Test
    void keepsAlreadyDecodedAuthorizationCode() {
        assertEquals("v^1.1#abc", EbayTokenService.authorizationCodeFrom("v^1.1#abc"));
    }
}
