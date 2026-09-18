package com.thetimelessvault.shopify;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopifySkuBackfillServiceTest {

    @Mock InventoryItemRepository items;
    @Mock ChannelListingRepository listings;
    @Mock ShopifyClient shopify;

    ShopifySkuBackfillService service;

    @BeforeEach
    void setUp() {
        service = new ShopifySkuBackfillService(items, listings, shopify);
        when(listings.findByInventoryItemIdAndPlatform(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void overwritesBlankAndMismatchedSkusOnActiveAndUnlistedListings() {
        InventoryItem blank = item("40765", "TTV-40765-5013");
        InventoryItem mismatch = item("75192-1", "TTV-75192-1-AAAA");
        ChannelListing active = published(blank, "gid://shopify/Product/1", "https://thetimelessvault.com/products/40765");
        active.setShopifyStatus("ACTIVE");
        ChannelListing unlisted = published(mismatch, "gid://shopify/Product/2", "https://thetimelessvault.com/products/millennium-falcon");
        unlisted.setShopifyStatus("UNLISTED");
        when(items.findAllWithCatalog()).thenReturn(List.of(blank, mismatch));
        when(listings.findByInventoryItemIdAndPlatform(blank.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(active));
        when(listings.findByInventoryItemIdAndPlatform(mismatch.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(unlisted));
        when(shopify.productSku("gid://shopify/Product/1", "40765")).thenReturn(null);
        when(shopify.productSku("gid://shopify/Product/2", "millennium-falcon")).thenReturn("old-sku");

        ShopifySkuBackfillService.Report report = service.run(false);

        assertEquals(2, report.candidates());
        assertEquals(2, report.updated());
        assertEquals("blank -> TTV-40765-5013", report.rows().get(0).message());
        assertEquals("old-sku -> TTV-75192-1-AAAA", report.rows().get(1).message());
        verify(shopify).updateProductSku("gid://shopify/Product/1", "40765", "TTV-40765-5013");
        verify(shopify).updateProductSku("gid://shopify/Product/2", "millennium-falcon", "TTV-75192-1-AAAA");
    }

    @Test
    void dryRunDoesNotWrite() {
        InventoryItem item = item("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, "gid://shopify/Product/1", null)));
        when(shopify.productSku("gid://shopify/Product/1", null)).thenReturn(null);

        ShopifySkuBackfillService.Report report = service.run(true);

        assertTrue(report.dryRun());
        assertEquals(1, report.updated());
        verify(shopify, never()).updateProductSku(any(), any(), any());
    }

    @Test
    void skipsWhenShopifySkuAlreadyMatches() {
        InventoryItem item = item("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, "gid://shopify/Product/1", null)));
        when(shopify.productSku("gid://shopify/Product/1", null)).thenReturn("ttv-40765-5013");

        ShopifySkuBackfillService.Report report = service.run(false);

        assertEquals(1, report.skipped());
        assertEquals("Shopify already TTV-40765-5013", report.rows().getFirst().message());
        verify(shopify, never()).updateProductSku(any(), any(), any());
    }

    @Test
    void ignoresItemsWithoutAShopifyListing() {
        InventoryItem item = item("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));

        ShopifySkuBackfillService.Report report = service.run(false);

        assertEquals(0, report.candidates());
        verify(shopify, never()).productSku(any(), any());
        verify(shopify, never()).updateProductSku(any(), any(), any());
    }

    @Test
    void recordsFailureWhenShopifyReadFails() {
        InventoryItem item = item("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, "gid://shopify/Product/1", null)));
        when(shopify.productSku(eq("gid://shopify/Product/1"), any()))
                .thenThrow(new RuntimeException("Shopify product not found"));

        ShopifySkuBackfillService.Report report = service.run(false);

        assertEquals(1, report.failed());
        assertEquals("FAIL", report.rows().getFirst().action());
        assertEquals("Shopify product not found", report.rows().getFirst().message());
        verify(shopify, never()).updateProductSku(any(), any(), any());
    }

    private static InventoryItem item(String setNumber, String sku) {
        CatalogItem catalog = CatalogItem.create(setNumber);
        catalog.setName(setNumber);
        InventoryItem item = InventoryItem.create(catalog, sku);
        item.setTitle("LEGO " + setNumber);
        item.setEbayPrice(new BigDecimal("99.00"));
        item.setBricklinkPrice(new BigDecimal("99.00"));
        item.setShopifyPrice(new BigDecimal("99.00"));
        item.setPrice(new BigDecimal("99.00"));
        return item;
    }

    private static ChannelListing published(InventoryItem item, String externalId, String liveUrl) {
        ChannelListing listing = ChannelListing.create(item, Platform.SHOPIFY);
        listing.markPublished(externalId, liveUrl, new BigDecimal("50.00"));
        return listing;
    }
}
