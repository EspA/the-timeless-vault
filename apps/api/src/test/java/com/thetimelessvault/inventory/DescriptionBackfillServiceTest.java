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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DescriptionBackfillServiceTest {

    @Mock InventoryItemRepository items;
    @Mock ChannelListingRepository listings;
    @Mock ShopifyClient shopify;
    @Mock BrickLinkClient brickLink;

    DescriptionBackfillService service;
    List<InventoryItem> saved;

    @BeforeEach
    void setUp() {
        service = new DescriptionBackfillService(items, listings, shopify, brickLink, passthroughTransactions());
        saved = new ArrayList<>();
        when(items.save(any())).thenAnswer(invocation -> {
            InventoryItem item = invocation.getArgument(0);
            saved.add(item);
            return item;
        });
        when(listings.findByInventoryItemIdAndPlatform(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void copiesShopifyHtmlAndBrickLinkShortDescription() throws Exception {
        InventoryItem item = blankItem("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(items.findWithCatalogById(item.getId())).thenReturn(Optional.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, Platform.SHOPIFY, "gid://shopify/Product/1", "https://thetimelessvault.com/products/40765")));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK))
                .thenReturn(Optional.of(published(item, Platform.BRICKLINK, "525841562", null)));
        when(shopify.productDescriptionHtml("gid://shopify/Product/1", "40765"))
                .thenReturn("<p>New sealed Kamino Training Facility.</p>");
        when(brickLink.getInventory("525841562")).thenReturn(new ObjectMapper().readTree("""
                { "inventory_id": 525841562, "description": "NISB, no price sticker" }
                """));

        DescriptionBackfillService.Report report = service.run(false);

        assertEquals(1, report.updated());
        assertEquals(0, report.skipped());
        assertEquals("<p>New sealed Kamino Training Facility.</p>", item.getDescription());
        assertEquals("NISB, no price sticker", item.getShortDescription());
        assertEquals("Shopify HTML; BrickLink short description", report.rows().getFirst().message());
        assertEquals(1, saved.size());
    }

    @Test
    void dryRunDoesNotWrite() {
        InventoryItem item = blankItem("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, Platform.SHOPIFY, "gid://shopify/Product/1", null)));
        when(shopify.productDescriptionHtml("gid://shopify/Product/1", null))
                .thenReturn("<p>New sealed.</p>");

        DescriptionBackfillService.Report report = service.run(true);

        assertTrue(report.dryRun());
        assertEquals(1, report.updated());
        assertNull(item.getDescription());
        verify(items, never()).save(any());
    }

    @Test
    void skipsItemsThatAlreadyHaveDescriptions() {
        InventoryItem item = blankItem("40765", "TTV-40765-5013");
        item.setDescription("<p>Already filled</p>");
        item.setShortDescription("Already filled");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));

        DescriptionBackfillService.Report report = service.run(false);

        assertEquals(0, report.candidates());
        verify(shopify, never()).productDescriptionHtml(any(), any());
        verify(items, never()).save(any());
    }

    @Test
    void skipsWhenNeitherListingIsLinked() {
        InventoryItem item = blankItem("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(items.findWithCatalogById(item.getId())).thenReturn(Optional.of(item));

        DescriptionBackfillService.Report report = service.run(false);

        assertEquals(1, report.skipped());
        assertEquals("SKIP", report.rows().getFirst().action());
        assertTrue(report.rows().getFirst().message().contains("Shopify listing not linked"));
        assertTrue(report.rows().getFirst().message().contains("BrickLink listing not linked"));
        verify(items, never()).save(any());
    }

    @Test
    void updatesShopifyOnlyWhenBrickLinkIsMissing() {
        InventoryItem item = blankItem("40765", "TTV-40765-5013");
        when(items.findAllWithCatalog()).thenReturn(List.of(item));
        when(items.findWithCatalogById(item.getId())).thenReturn(Optional.of(item));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(published(item, Platform.SHOPIFY, "gid://shopify/Product/1", null)));
        when(shopify.productDescriptionHtml("gid://shopify/Product/1", null))
                .thenReturn("<p>From Shopify only.</p>");

        DescriptionBackfillService.Report report = service.run(false);

        assertEquals(1, report.updated());
        assertEquals("<p>From Shopify only.</p>", item.getDescription());
        assertNull(item.getShortDescription());
        assertTrue(report.rows().getFirst().message().contains("BrickLink listing not linked"));
    }

    private static InventoryItem blankItem(String setNumber, String sku) {
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

    private static ChannelListing published(InventoryItem item, Platform platform, String externalId, String liveUrl) {
        ChannelListing listing = ChannelListing.create(item, platform);
        listing.markPublished(externalId, liveUrl, new BigDecimal("99.00"));
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
