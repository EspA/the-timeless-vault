package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class ShopifyTokenServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private AppProperties properties;
    private MockRestServiceServer server;
    private ShopifyTokenService tokens;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getShopify().setShopDomain("thetimelessvault.myshopify.com");
        properties.getShopify().setClientId("client-id");
        properties.getShopify().setClientSecret("client-secret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokens = new ShopifyTokenService(properties, mapper, builder.build());
    }

    @Test
    void configuredWhenShopAndClientCredentialsAreSet() {
        assertTrue(properties.getShopify().configured());
        assertTrue(properties.getShopify().clientCredentialsConfigured());
        assertTrue(tokens.refreshable());
    }

    @Test
    void configuredWhenOnlyStaticAdminTokenIsSet() {
        AppProperties.Shopify shopify = new AppProperties().getShopify();
        shopify.setShopDomain("thetimelessvault.myshopify.com");
        shopify.setAdminToken("shpat_static");
        assertTrue(shopify.configured());
        assertFalse(shopify.clientCredentialsConfigured());
    }

    @Test
    void notConfiguredWithoutTokenOrCredentials() {
        AppProperties.Shopify shopify = new AppProperties().getShopify();
        shopify.setShopDomain("thetimelessvault.myshopify.com");
        assertFalse(shopify.configured());
    }

    @Test
    void stripsProtocolFromShopHost() {
        properties.getShopify().setShopDomain("https://thetimelessvault.myshopify.com/");
        assertEquals("thetimelessvault.myshopify.com", properties.getShopify().shopHost());
    }

    @Test
    void fetchesAndCachesClientCredentialsToken() {
        expectToken("shpat_fresh");
        assertEquals("shpat_fresh", tokens.accessToken());
        assertTrue(tokens.hasScope("read_orders"));
        assertFalse(tokens.hasScope("read_customers"));
        server.verify();
    }

    @Test
    void refetchesAfterInvalidate() {
        expectToken("shpat_one");
        expectToken("shpat_two");
        assertEquals("shpat_one", tokens.accessToken());
        tokens.invalidate();
        assertEquals("shpat_two", tokens.accessToken());
        server.verify();
    }

    @Test
    void fallsBackToStaticAdminTokenWhenCredentialsMissing() {
        properties.getShopify().setClientId("");
        properties.getShopify().setClientSecret("");
        properties.getShopify().setAdminToken("shpat_static");
        assertEquals("shpat_static", tokens.accessToken());
        server.verify();
    }

    @Test
    void prefersClientCredentialsOverStaleStaticToken() {
        properties.getShopify().setAdminToken("shpat_stale");
        expectToken("shpat_fresh");
        assertEquals("shpat_fresh", tokens.accessToken());
        server.verify();
    }

    @Test
    void wrapsShopifyTokenError() {
        server.expect(once(), requestTo("https://thetimelessvault.myshopify.com/admin/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest()
                        .body("{\"error\":\"invalid_client\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        ApiException error = assertThrows(ApiException.class, tokens::accessToken);
        assertTrue(error.getMessage().contains("invalid_client"));
    }

    @Test
    void readsOauthErrorBody() {
        assertEquals("invalid_client — bad secret",
                ShopifyTokenService.oauthErrorMessage(
                        "{\"error\":\"invalid_client\",\"error_description\":\"bad secret\"}", "401"));
    }

    private void expectToken(String accessToken) {
        server.expect(once(), requestTo("https://thetimelessvault.myshopify.com/admin/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + accessToken + "\",\"scope\":\"write_products,read_orders\",\"expires_in\":86399}",
                        MediaType.APPLICATION_JSON));
    }
}
