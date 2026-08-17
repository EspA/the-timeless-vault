package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EbayStoreCategoriesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void flattensNestedStoreCategoriesToListingPaths() throws Exception {
        var root = mapper.readTree("""
                {
                  "storeCategories": [
                    {
                      "categoryId": "10",
                      "categoryName": "Star Wars",
                      "childrenCategories": [
                        { "categoryId": "11", "categoryName": "UCS" }
                      ]
                    },
                    { "categoryId": "20", "categoryName": "Marvel" }
                  ]
                }
                """);

        List<Map<String, String>> categories = EbayStoreCategories.flatten(root);
        assertEquals(3, categories.size());
        assertEquals("Star Wars", categories.get(0).get("path"));
        assertEquals("Star Wars/UCS", categories.get(1).get("path"));
        assertEquals("Marvel", categories.get(2).get("path"));
    }
}
