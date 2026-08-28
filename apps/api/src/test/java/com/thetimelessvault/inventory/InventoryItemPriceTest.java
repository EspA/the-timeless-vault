package com.thetimelessvault.inventory;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryItemPriceTest {

    @Test
    void usesEachChannelPriceIndependently() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75870-1"), "SKU-75870");
        item.setPrice(new BigDecimal("100.00"));
        item.setEbayPrice(new BigDecimal("145.00"));
        item.setBricklinkPrice(new BigDecimal("140.00"));
        item.setShopifyPrice(new BigDecimal("132.00"));
        item.setBrickowlPrice(new BigDecimal("140.00"));

        assertEquals(new BigDecimal("145.00"), item.priceFor(Platform.EBAY));
        assertEquals(new BigDecimal("140.00"), item.priceFor(Platform.BRICKLINK));
        assertEquals(new BigDecimal("132.00"), item.priceFor(Platform.SHOPIFY));
        assertEquals(new BigDecimal("140.00"), item.priceFor(Platform.BRICKOWL));
        assertEquals("145.00", ChannelPrice.required(item, Platform.EBAY));
        assertEquals("140.00", ChannelPrice.required(item, Platform.BRICKLINK));
        assertEquals("132.00", ChannelPrice.required(item, Platform.SHOPIFY));
        assertEquals("140.00", ChannelPrice.required(item, Platform.BRICKOWL));

        InventoryItem legacy = InventoryItem.create(CatalogItem.create("10236-1"), "SKU-10236");
        legacy.setPrice(new BigDecimal("249.99"));
        assertNull(legacy.priceFor(Platform.EBAY));
        assertNull(legacy.priceFor(Platform.BRICKLINK));
        assertNull(legacy.priceFor(Platform.SHOPIFY));
        assertNull(legacy.priceFor(Platform.BRICKOWL));
        assertThrows(com.thetimelessvault.common.ApiException.class, () -> ChannelPrice.required(legacy, Platform.SHOPIFY));
    }
}
