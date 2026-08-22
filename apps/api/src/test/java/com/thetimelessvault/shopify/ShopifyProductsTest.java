package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopifyProductsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsAdminProductsAndNormalizesGids() throws Exception {
        List<ShopifyActiveListing> listings = ShopifyProducts.parseAdmin(mapper.readTree("""
                [
                  {
                    "id": "gid://shopify/Product/123",
                    "title": "LEGO 7665 Republic Cruiser",
                    "handle": "lego-7665-republic-cruiser",
                    "onlineStoreUrl": "https://thetimelessvault.com/products/lego-7665-republic-cruiser",
                    "variants": {
                      "nodes": [
                        { "sku": "TTV-7665-AAAA", "price": "349.99", "inventoryQuantity": 1 }
                      ]
                    }
                  }
                ]
                """));

        assertEquals(1, listings.size());
        ShopifyActiveListing listing = listings.getFirst();
        assertEquals("gid://shopify/Product/123", listing.productId());
        assertEquals("TTV-7665-AAAA", listing.sku());
        assertEquals("7665", listing.setNumber());
        assertEquals(new BigDecimal("349.99"), listing.price());
        assertEquals(1, listing.quantity());
    }

    @Test
    void readsStorefrontNumericIds() throws Exception {
        List<ShopifyActiveListing> listings = ShopifyProducts.parseStorefront(mapper.readTree("""
                [
                  {
                    "id": 456,
                    "title": "LEGO Star Wars 75192 UCS Millennium Falcon",
                    "handle": "millennium-falcon",
                    "variants": [
                      { "sku": "", "price": "849.99", "inventory_quantity": 1 }
                    ]
                  }
                ]
                """));

        assertEquals(1, listings.size());
        assertEquals("gid://shopify/Product/456", listings.getFirst().productId());
        assertEquals("75192", listings.getFirst().setNumber());
        assertEquals("https://thetimelessvault.com/products/millennium-falcon", listings.getFirst().liveUrl());
    }

    @Test
    void skipsProductsThatAreNotQuantityOne() throws Exception {
        List<ShopifyActiveListing> listings = ShopifyProducts.parseAdmin(mapper.readTree("""
                [
                  {
                    "id": "gid://shopify/Product/1",
                    "title": "LEGO 7665 Republic Cruiser",
                    "handle": "cruiser",
                    "variants": { "nodes": [{ "sku": "A", "price": "1.00", "inventoryQuantity": 2 }] }
                  },
                  {
                    "id": "gid://shopify/Product/2",
                    "title": "LEGO 7665 Republic Cruiser",
                    "handle": "cruiser-sold",
                    "variants": { "nodes": [{ "sku": "B", "price": "1.00", "inventoryQuantity": 0 }] }
                  }
                ]
                """));
        assertEquals(0, listings.size());
    }
}
