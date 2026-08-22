package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrickLinkInventoriesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsActiveSetLotsAndSkipsParts() throws Exception {
        List<BrickLinkActiveListing> listings = BrickLinkInventories.parse(mapper.readTree("""
                [
                  {
                    "inventory_id": 111,
                    "item": { "no": "7665-1", "name": "Republic Cruiser", "type": "SET" },
                    "quantity": 1,
                    "unit_price": "349.99",
                    "new_or_used": "N",
                    "completeness": "S",
                    "remarks": "TTV-7665-AAAA"
                  },
                  {
                    "inventory_id": 222,
                    "item": { "no": "3001", "name": "Brick 2 x 4", "type": "PART" },
                    "quantity": 40,
                    "new_or_used": "N"
                  }
                ]
                """));

        assertEquals(1, listings.size());
        BrickLinkActiveListing listing = listings.getFirst();
        assertEquals("111", listing.inventoryId());
        assertEquals("7665-1", listing.setNumber());
        assertEquals("Republic Cruiser", listing.title());
        assertEquals(1, listing.quantity());
        assertEquals(new BigDecimal("349.99"), listing.unitPrice());
        assertEquals("N", listing.newOrUsed());
        assertEquals("S", listing.completeness());
        assertEquals("TTV-7665-AAAA", listing.remarks());
    }

    @Test
    void ignoresLotsWithoutAnInventoryId() throws Exception {
        List<BrickLinkActiveListing> listings = BrickLinkInventories.parse(mapper.readTree("""
                [{ "item": { "no": "7665-1", "type": "SET" } }]
                """));
        assertTrue(listings.isEmpty());
    }
}
