package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.InventoryItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayOfferRequestTest {

    @Test
    void stagesFixedPriceOfferWithoutPublishingFields() {
        CatalogItem catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        InventoryItem item = InventoryItem.create(catalog, "SKU-75192");
        item.setPrice(new BigDecimal("1.00"));
        item.setEbayPrice(new BigDecimal("849.99"));
        item.setQuantity(1);
        item.setDescription("New sealed");
        item.setCondition(ItemCondition.NEW_SEALED);
        item.setEbayStoreCategory("Star Wars");
        item.setMinimumOffer(new BigDecimal("700.00"));

        AppProperties properties = new AppProperties();
        properties.getEbay().setMarketplaceId("EBAY_US");
        properties.getEbay().setCategoryId("19006");
        properties.getEbay().setMerchantLocationKey("warehouse");
        properties.getEbay().setFulfillmentPolicyId("fulfill");
        properties.getEbay().setPaymentPolicyId("pay");
        properties.getEbay().setReturnPolicyId("return");

        ObjectNode body = new EbayClient(properties, null, new ObjectMapper()).offerRequest(item);

        assertEquals("SKU-75192", body.path("sku").asText());
        assertEquals("EBAY_US", body.path("marketplaceId").asText());
        assertEquals("FIXED_PRICE", body.path("format").asText());
        assertEquals(1, body.path("availableQuantity").asInt());
        assertEquals("19006", body.path("categoryId").asText());
        assertEquals("warehouse", body.path("merchantLocationKey").asText());
        assertEquals("849.99", body.path("pricingSummary").path("price").path("value").asText());
        assertEquals("USD", body.path("pricingSummary").path("price").path("currency").asText());
        assertEquals("fulfill", body.path("listingPolicies").path("fulfillmentPolicyId").asText());
        assertEquals("pay", body.path("listingPolicies").path("paymentPolicyId").asText());
        assertEquals("return", body.path("listingPolicies").path("returnPolicyId").asText());
        assertEquals("700.00", body.path("listingPolicies").path("bestOfferTerms").path("autoDeclinePrice").path("value").asText());
        assertEquals("/Star Wars", body.path("storeCategoryNames").path(0).asText());
        assertFalse(body.has("listingStartDate"));
    }

    @Test
    void listingUrlUsesItemId() {
        assertEquals("https://www.ebay.com/itm/123456789", EbayClient.listingUrl("123456789"));
        assertNull(EbayClient.listingUrl(null));
        assertNull(EbayClient.listingUrl(" "));
    }

    @Test
    void inventoryItemKeepsSellerCopyAndAppliesCatalogTemplate() {
        CatalogItem catalog = CatalogItem.create("75870-1");
        catalog.setName("Chevrolet Corvette Z06");
        catalog.setTheme("Speed Champions");
        InventoryItem item = InventoryItem.create(catalog, "SKU-75870");
        item.setTitle("My sealed Corvette");
        item.setDescription("My photos and notes");
        item.setCondition(ItemCondition.NEW_SEALED);
        item.setQuantity(1);
        item.setPackageLbs(2);
        item.setPackageOz(0);

        EbayCatalogTemplate template = new EbayCatalogTemplate(
                "12053416333",
                "75870",
                java.util.List.of("673419247252"),
                java.util.List.of("5702015591508"),
                java.util.Map.of(
                        "Brand", java.util.List.of("LEGO"),
                        "Age Level", java.util.List.of("7-14"),
                        "Interests", java.util.List.of("Cars, Vehicles"),
                        "Type", java.util.List.of("Complete Set"),
                        "Year Retired", java.util.List.of("2017"),
                        "Retired", java.util.List.of("Yes")
                )
        );

        ObjectNode body = new EbayClient(new AppProperties(), null, new ObjectMapper())
                .inventoryItemRequest(item, java.util.List.of("https://storage.example/photo.jpg"), template);

        assertEquals("My sealed Corvette", body.path("product").path("title").asText());
        assertEquals("https://storage.example/photo.jpg", body.path("product").path("imageUrls").path(0).asText());
        assertEquals("12053416333", body.path("product").path("epid").asText());
        assertEquals("LEGO", body.path("product").path("brand").asText());
        assertEquals("75870", body.path("product").path("mpn").asText());
        assertEquals("673419247252", body.path("product").path("upc").path(0).asText());
        assertEquals("Complete Set", body.path("product").path("aspects").path("Type").path(0).asText());
        assertEquals("2017", body.path("product").path("aspects").path("Year Retired").path(0).asText());
        assertEquals("Yes", body.path("product").path("aspects").path("Retired").path(0).asText());
        assertTrue(body.path("product").path("aspects").path("Age Level").isMissingNode());
        assertEquals("PACKAGE_THICK_ENVELOPE", body.path("packageWeightAndSize").path("packageType").asText());
    }
}
