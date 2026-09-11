package com.thetimelessvault.inventory;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.BoxGrade;
import com.thetimelessvault.common.DefaultListingCopy;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.StockStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryCsvTest {

    @Test
    void rendersInventoryListingAndPriceColumns() {
        InventoryItem item = InventoryItem.create(CatalogItem.create("10134-1"), "TTV-10134-ABCD");
        item.setTitle("LEGO 10134 VERY RARE Star Wars UCS Y-Wing, New Sealed");
        item.setDescription(DefaultListingCopy.description(item.getCatalogItem(), ItemCondition.NEW_SEALED, BoxGrade.GRADE_10));
        item.setShortDescription("NISB box grade 10");
        item.setPrice(new BigDecimal("1500"));
        item.setShopifyPrice(new BigDecimal("1599"));
        item.setEbayPrice(new BigDecimal("1699.5"));
        item.setBricklinkPrice(new BigDecimal("1549"));
        item.setBrickowlPrice(new BigDecimal("1575"));
        item.setCost(new BigDecimal("900"));
        item.setMinimumOffer(new BigDecimal("1400"));
        item.setStockStatus(StockStatus.IN_STOCK);
        item.setQuantity(1);

        InventoryItem missingGrade = InventoryItem.create(CatalogItem.create("40755"), "TTV-40755-WXYZ");
        missingGrade.setTitle("LEGO 40755 Imperial Dropship");
        missingGrade.setDescription("<p>No box grade in this listing.</p>");
        missingGrade.setShopifyPrice(new BigDecimal("69.00"));
        missingGrade.setStockStatus(StockStatus.IN_TRANSIT);
        missingGrade.setQuantity(0);

        String csv = InventoryCsv.render(List.of(item, missingGrade));

        assertEquals(19, InventoryCsv.COLUMNS.size());
        assertTrue(csv.startsWith("\uFEFF" + InventoryCsv.HEADER + "\n"));
        assertTrue(csv.contains("Id,SKU,Set id,Set number,Title,"));
        assertTrue(csv.contains("BrickOwl price,Minimum offer"));
        assertFalse(csv.contains("Catalog name"));
        assertFalse(csv.contains("Shopify status"));
        assertFalse(csv.contains("Photo count"));
        assertTrue(csv.contains("\"LEGO 10134 VERY RARE Star Wars UCS Y-Wing, New Sealed\""));
        assertTrue(csv.contains("TTV-10134-ABCD"));
        assertTrue(csv.contains("10134-1"));
        assertTrue(csv.contains(",10,"));
        assertTrue(csv.contains("1599.00"));
        assertTrue(csv.contains("1699.50"));
        assertTrue(csv.contains("1549.00"));
        assertTrue(csv.contains("1575.00"));
        assertTrue(csv.contains("900.00"));
        assertTrue(csv.contains("1400.00"));
        assertTrue(csv.contains("IN_STOCK"));
        assertTrue(csv.contains("NEW_SEALED"));
        assertTrue(csv.contains("SET"));
        assertTrue(csv.contains("No box grade in this listing."));
        assertFalse(csv.contains("<p>No box grade"));
        assertTrue(csv.contains("IN_TRANSIT"));
        assertEquals(3, csv.lines().count());
    }
}
