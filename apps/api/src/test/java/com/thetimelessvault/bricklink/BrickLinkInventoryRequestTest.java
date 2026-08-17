package com.thetimelessvault.bricklink;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrickLinkInventoryRequestTest {

    @Test
    void createsLotsInStockRoomWithoutRetainingSoldOutItems() {
        CatalogItem catalog = CatalogItem.create("10236-1");
        catalog.setName("Old Trafford");
        InventoryItem item = InventoryItem.create(catalog, "SKU-10236");
        item.setPrice(new BigDecimal("249.99"));
        item.setBricklinkPrice(new BigDecimal("199.99"));
        item.setQuantity(1);
        item.setShortDescription("New in sealed box");
        item.setCondition(ItemCondition.NEW_SEALED);

        ObjectNode body = new BrickLinkClient(new AppProperties(), new ObjectMapper()).inventoryRequest(item);

        assertEquals("10236-1", body.path("item").path("no").asText());
        assertEquals("SET", body.path("item").path("type").asText());
        assertEquals(0, body.path("color_id").asInt());
        assertEquals(1, body.path("quantity").asInt());
        assertEquals("199.99", body.path("unit_price").asText());
        assertEquals("N", body.path("new_or_used").asText());
        assertEquals("S", body.path("completeness").asText());
        assertEquals("New in sealed box", body.path("description").asText());
        assertEquals("SKU-10236", body.path("remarks").asText());
        assertEquals(1, body.path("bulk").asInt());
        assertFalse(body.path("is_retain").asBoolean());
        assertTrue(body.path("is_stock_room").asBoolean());
        assertEquals("A", body.path("stock_room_id").asText());
    }

    @Test
    void stockRoomUpdateListsOrHidesTheLot() {
        BrickLinkClient client = new BrickLinkClient(new AppProperties(), new ObjectMapper());

        ObjectNode unlist = client.stockRoomRequest(true);
        assertTrue(unlist.path("is_stock_room").asBoolean());
        assertEquals("A", unlist.path("stock_room_id").asText());

        ObjectNode list = client.stockRoomRequest(false);
        assertFalse(list.path("is_stock_room").asBoolean());
        assertTrue(list.path("stock_room_id").isMissingNode());
    }
}
