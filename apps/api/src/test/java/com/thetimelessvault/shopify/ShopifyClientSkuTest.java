package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class ShopifyClientSkuTest {

    private static final String GRAPHQL_URL = "https://thetimelessvault.myshopify.com/admin/api/2025-10/graphql.json";

    @Mock
    ShopifyTokenService tokens;

    private final ObjectMapper mapper = new ObjectMapper();
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
        client = new ShopifyClient(properties, mapper, tokens, builder.build());
        when(tokens.accessToken()).thenReturn("token");
    }

    @Test
    void readsSkuFromTheFirstVariant() {
        server.expect(once(), requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"product":{"id":"gid://shopify/Product/1","variants":{"nodes":[{"id":"gid://shopify/ProductVariant/9","sku":"OLD"}]}}}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("OLD", client.productSku("gid://shopify/Product/1", null));
        server.verify();
    }

    @Test
    void updateProductSkuSendsOnlyTheSkuField() {
        server.expect(once(), requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"product":{"id":"gid://shopify/Product/1","variants":{"nodes":[{"id":"gid://shopify/ProductVariant/9","sku":""}]}}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsBytes());
                    JsonNode variables = body.path("variables");
                    JsonNode variant = variables.path("variants").path(0);
                    assertEquals("gid://shopify/Product/1", variables.path("productId").asText());
                    assertEquals("gid://shopify/ProductVariant/9", variant.path("id").asText());
                    assertEquals("TTV-40765-5013", variant.path("inventoryItem").path("sku").asText());
                    assertFalse(variant.has("price"));
                    assertFalse(variant.path("inventoryItem").has("tracked"));
                    assertFalse(variant.path("inventoryItem").has("measurement"));
                    assertFalse(body.path("query").asText().contains("productUpdate"));
                    assertFalse(body.path("query").asText().contains("media"));
                })
                .andRespond(withSuccess("""
                        {"data":{"productVariantsBulkUpdate":{"userErrors":[]}}}
                        """, MediaType.APPLICATION_JSON));

        client.updateProductSku("1", null, "TTV-40765-5013");
        server.verify();
    }
}
