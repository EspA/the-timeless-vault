package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

@ExtendWith(MockitoExtension.class)
class ShopifyClientAuthTest {

    private static final String GRAPHQL_URL = "https://thetimelessvault.myshopify.com/admin/api/2025-10/graphql.json";

    @Mock
    ShopifyTokenService tokens;

    private MockRestServiceServer server;
    private ShopifyClient client;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getShopify().setShopDomain("thetimelessvault.myshopify.com");
        properties.getShopify().setClientId("client-id");
        properties.getShopify().setClientSecret("client-secret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ShopifyClient(properties, new ObjectMapper(), tokens, builder.build());
    }

    @Test
    void refreshesTokenAndRetriesGraphqlOn401() {
        when(tokens.accessToken()).thenReturn("stale-token", "fresh-token");
        when(tokens.refreshable()).thenReturn(true);
        server.expect(once(), requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Shopify-Access-Token", "stale-token"))
                .andRespond(withUnauthorizedRequest());
        server.expect(once(), requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Shopify-Access-Token", "fresh-token"))
                .andRespond(withSuccess("""
                        {"data":{"shop":{"name":"The Timeless Vault"}}}
                        """, MediaType.APPLICATION_JSON));

        client.graphql("query { shop { name } }", new ObjectMapper().createObjectNode());

        verify(tokens).invalidate();
        server.verify();
    }

    @Test
    void doesNotRetry401WithoutClientCredentials() {
        when(tokens.accessToken()).thenReturn("static-token");
        when(tokens.refreshable()).thenReturn(false);
        server.expect(once(), requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Shopify-Access-Token", "static-token"))
                .andRespond(withUnauthorizedRequest());

        ApiException error = assertThrows(ApiException.class,
                () -> client.graphql("query { shop { name } }", new ObjectMapper().createObjectNode()));
        assertTrue(error.getMessage().contains("SHOPIFY_CLIENT_ID"));
        verify(tokens, never()).invalidate();
        server.verify();
    }
}
