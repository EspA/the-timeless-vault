package com.thetimelessvault.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.shopify.ShopifyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceBackfillServiceTest {

    @Mock InventoryItemRepository items;
    @Mock ChannelListingRepository listings;
    @Mock ShopifyClient shopify;
    @Mock BrickLinkClient brickLink;

    PriceBackfillService service;
    List<InventoryItem> saved;

    @BeforeEach
    void setUp() {
        service = new PriceBackfillService(items, listings, shopify, brickLink, passthroughTransactions());
        saved = new ArrayList<>();
        when(items.save(any())).thenAnswer(invocation -> {
            InventoryItem item = invocation.getArgument(0);
            saved.add(item);
            return item;
        });
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(listings.findByInventoryItemIdAndPlatform(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void copiesShopifyAndBrickLinkPricesAndLeavesEbayAlone() throws Exception {
        InventoryItem item = priced("40765", "TTV-40765-5013", "50.00", "50.00", "175.00");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(items.findWithCatalogById(item.getId())).thenReturn(Optional.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, Platform.SHOPIFY, "gid://shopify/Product/1", "https://thetimelessvault.com/products/40765")));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK))
                .thenReturn(Optional.of(published(item, Platform.BRICKLINK, "525841562", null)));
        when(shopify.productPrice("gid://shopify/Product/1", "40765")).thenReturn(new BigDecimal("89.99"));
        when(brickLink.getInventory("525841562")).thenReturn(new ObjectMapper().readTree("""
                { "inventory_id": 525841562, "unit_price": "79.50" }
                """));

        PriceBackfillService.Report report = service.run(false);

        assertEquals(1, report.updated());
        assertEquals(new BigDecimal("89.99"), item.getShopifyPrice());
        assertEquals(new BigDecimal("79.50"), item.getBricklinkPrice());
        assertEquals(new BigDecimal("175.00"), item.getEbayPrice());
        assertTrue(report.rows().getFirst().message().contains("Shopify 50.00 -> 89.99"));
        assertTrue(report.rows().getFirst().message().contains("BrickLink 50.00 -> 79.50"));
        assertEquals(1, saved.size());
    }

    @Test
    void dryRunDoesNotWrite() {
        InventoryItem item = priced("40765", "TTV-40765-5013", "50.00", "50.00", "175.00");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, Platform.SHOPIFY, "gid://shopify/Product/1", null)));
        when(shopify.productPrice("gid://shopify/Product/1", null)).thenReturn(new BigDecimal("89.99"));

        PriceBackfillService.Report report = service.run(true);

        assertTrue(report.dryRun());
        assertEquals(1, report.updated());
        assertEquals(new BigDecimal("50.00"), item.getShopifyPrice());
        verify(items, never()).save(any());
    }

    @Test
    void skipsWhenPricesAlreadyMatch() throws Exception {
        InventoryItem item = priced("40765", "TTV-40765-5013", "89.99", "79.50", "175.00");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(items.findWithCatalogById(item.getId())).thenReturn(Optional.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, Platform.SHOPIFY, "gid://shopify/Product/1", null)));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK))
                .thenReturn(Optional.of(published(item, Platform.BRICKLINK, "525841562", null)));
        when(shopify.productPrice("gid://shopify/Product/1", null)).thenReturn(new BigDecimal("89.99"));
        when(brickLink.getInventory("525841562")).thenReturn(new ObjectMapper().readTree("""
                { "unit_price": "79.50" }
                """));

        PriceBackfillService.Report report = service.run(false);

        assertEquals(1, report.skipped());
        verify(items, never()).save(any());
    }

    @Test
    void skipsWhenNeitherListingIsLinked() {
        InventoryItem item = priced("40765", "TTV-40765-5013", "50.00", "50.00", "175.00");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(items.findWithCatalogById(item.getId())).thenReturn(Optional.of(item));

        PriceBackfillService.Report report = service.run(false);

        assertEquals(1, report.skipped());
        assertTrue(report.rows().getFirst().message().contains("Shopify listing not linked"));
        assertTrue(report.rows().getFirst().message().contains("BrickLink listing not linked"));
        verify(items, never()).save(any());
    }

    private static InventoryItem priced(
            String setNumber,
            String sku,
            String shopify,
            String bricklink,
            String ebay
    ) {
        CatalogItem catalog = CatalogItem.create(setNumber);
        catalog.setName(setNumber);
        InventoryItem item = InventoryItem.create(catalog, sku);
        item.setTitle("LEGO " + setNumber);
        item.setShopifyPrice(new BigDecimal(shopify));
        item.setBricklinkPrice(new BigDecimal(bricklink));
        item.setEbayPrice(new BigDecimal(ebay));
        item.setPrice(new BigDecimal(ebay));
        return item;
    }

    private static ChannelListing published(InventoryItem item, Platform platform, String externalId, String liveUrl) {
        ChannelListing listing = ChannelListing.create(item, platform);
        listing.markPublished(externalId, liveUrl, new BigDecimal("50.00"));
        return listing;
    }

    private static PlatformTransactionManager passthroughTransactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
