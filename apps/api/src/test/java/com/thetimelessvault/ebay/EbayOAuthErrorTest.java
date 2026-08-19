package com.thetimelessvault.ebay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void sellConsentIncludesWriteAndReadonlyStoreScopes() {
        String scopes = EbayTokenService.sellScopes();
        assertTrue(scopes.contains(EbayTokenService.STORE_SCOPE));
        assertTrue(scopes.contains(EbayTokenService.STORE_READONLY_SCOPE));
        assertTrue(EbayTokenService.legacySellScopes().contains(EbayTokenService.STORE_SCOPE));
        assertFalse(EbayTokenService.legacySellScopes().contains(EbayTokenService.STORE_READONLY_SCOPE));
    }

    @Test
    void detectsStoreScopeOnAccessToken() {
        assertTrue(EbayTokenService.hasStoreScope(
                "https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.stores"));
        assertFalse(EbayTokenService.hasStoreScope("https://api.ebay.com/oauth/api_scope/sell.inventory"));
        assertTrue(EbayTokenService.isInvalidScope("eBay token request failed: invalid_scope — requested scope exceeds grant"));
        assertFalse(EbayTokenService.isInvalidScope("invalid_grant"));
    }
}
