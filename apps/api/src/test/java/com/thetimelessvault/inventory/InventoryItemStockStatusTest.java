package com.thetimelessvault.inventory;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.StockStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryItemStockStatusTest {

    @Test
    void newItemsStartInTransitWithZeroQuantity() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");

        assertEquals(StockStatus.IN_TRANSIT, item.getStockStatus());
        assertEquals(0, item.getQuantity());
    }

    @Test
    void applyingInStockBumpsQuantityByOne() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");

        item.applyStockAndQuantity(StockStatus.IN_STOCK, 0);

        assertEquals(StockStatus.IN_STOCK, item.getStockStatus());
        assertEquals(1, item.getQuantity());
    }

    @Test
    void applyingInStockKeepsAHigherRequestedQuantity() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");

        item.applyStockAndQuantity(StockStatus.IN_STOCK, 5);

        assertEquals(5, item.getQuantity());
    }

    @Test
    void stayingInStockDoesNotBumpQuantityAgain() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");
        item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);

        item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);

        assertEquals(1, item.getQuantity());
    }

    @Test
    void applyingSoldZerosQuantity() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");
        item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);

        item.applyStockAndQuantity(StockStatus.SOLD, 1);

        assertEquals(StockStatus.SOLD, item.getStockStatus());
        assertEquals(0, item.getQuantity());
    }

    @Test
    void restockingFromSoldBumpsQuantityToOne() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");
        item.applyStockAndQuantity(StockStatus.SOLD, 0);

        item.applyStockAndQuantity(StockStatus.IN_STOCK, 0);

        assertEquals(StockStatus.IN_STOCK, item.getStockStatus());
        assertEquals(1, item.getQuantity());
    }

    @Test
    void applyingInTransitZerosQuantity() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");
        item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);

        item.applyStockAndQuantity(StockStatus.IN_TRANSIT, 1);

        assertEquals(StockStatus.IN_TRANSIT, item.getStockStatus());
        assertEquals(0, item.getQuantity());
    }

    @Test
    void changingStockStatusTouchesUpdatedAt() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1-AAAA");
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        setUpdatedAt(item, original);

        item.applyStockAndQuantity(StockStatus.IN_STOCK, 0);
        assertTrue(item.getUpdatedAt().isAfter(original));

        Instant afterInStock = item.getUpdatedAt();
        item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);
        assertEquals(afterInStock, item.getUpdatedAt());

        item.applyStockAndQuantity(StockStatus.SOLD, 0);
        assertTrue(item.getUpdatedAt().isAfter(afterInStock));
    }

    private static void setUpdatedAt(InventoryItem item, Instant updatedAt) {
        try {
            var field = InventoryItem.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(item, updatedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
