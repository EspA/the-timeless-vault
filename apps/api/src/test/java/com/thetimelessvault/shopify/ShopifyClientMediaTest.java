package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopifyClientMediaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void collectsMediaImageIdsFromNodes() throws Exception {
        var product = mapper.readTree("""
                {
                  "id": "gid://shopify/Product/1",
                  "media": {
                    "nodes": [
                      { "id": "gid://shopify/MediaImage/10" },
                      { "id": "gid://shopify/MediaImage/20" }
                    ]
                  }
                }
                """);
        assertEquals(
                List.of("gid://shopify/MediaImage/10", "gid://shopify/MediaImage/20"),
                ShopifyClient.collectMediaIds(product)
        );
    }

    @Test
    void collectsMediaIdsFromEdgesAndCreateMediaPayload() throws Exception {
        var product = mapper.readTree("""
                {
                  "media": {
                    "edges": [
                      { "node": { "id": "gid://shopify/MediaImage/10" } }
                    ]
                  }
                }
                """);
        assertEquals(List.of("gid://shopify/MediaImage/10"), ShopifyClient.collectMediaIds(product));

        var created = mapper.readTree("""
                {
                  "media": [
                    { "id": "gid://shopify/MediaImage/30" }
                  ]
                }
                """);
        assertEquals(List.of("gid://shopify/MediaImage/30"), ShopifyClient.collectMediaIds(created));
    }

    @Test
    void removesDeletedMediaAndKeepsReusedIds() {
        List<String> existing = List.of(
                "gid://shopify/MediaImage/keep",
                "gid://shopify/MediaImage/deleted"
        );
        assertEquals(
                List.of("gid://shopify/MediaImage/deleted"),
                ShopifyClient.mediaIdsToRemove(existing, List.of("gid://shopify/MediaImage/keep"))
        );
        assertEquals(existing, ShopifyClient.mediaIdsToRemove(existing, List.of()));
        assertEquals(List.of(), ShopifyClient.mediaIdsToRemove(List.of(), List.of("gid://shopify/MediaImage/new")));
    }
}
