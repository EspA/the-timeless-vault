package com.thetimelessvault.bricklink;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrickLinkListingImportServiceTest {

    @Mock BrickLinkClient brickLink;
    @Mock InventoryItemRepository items;
    @Mock ChannelListingRepository listings;

    BrickLinkListingImportService service;
    List<ChannelListing> saved;

    @BeforeEach
    void setUp() {
        service = new BrickLinkListingImportService(brickLink, items, listings, passthroughTransactions());
        saved = new ArrayList<>();
        when(brickLink.configured()).thenReturn(true);
        when(listings.save(any())).thenAnswer(invocation -> {
            ChannelListing listing = invocation.getArgument(0);
            saved.add(listing);
            return listing;
        });
        when(listings.findByInventoryItemIdAndPlatform(any(), any())).thenReturn(Optional.empty());
        when(listings.findByPlatformAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(listings.findByPlatformAndLiveUrl(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void linksOneListingPerDuplicateInStockCopy() {
        InventoryItem first = inStock("7665", "TTV-7665-AAAA", ItemCondition.NEW_SEALED, Instant.parse("2026-01-01T00:00:00Z"));
        InventoryItem second = inStock("7665", "TTV-7665-BBBB", ItemCondition.NEW_SEALED, Instant.parse("2026-01-02T00:00:00Z"));
        when(items.findWithCatalogByStockStatus(StockStatus.IN_STOCK)).thenReturn(List.of(first, second));
        when(brickLink.listActiveSetInventories()).thenReturn(List.of(
                lot("111", "7665-1", null, "N", "S", 1),
                lot("222", "7665-1", null, "N", "S", 1)
        ));

        BrickLinkListingImportService.Report report = service.run(false);

        assertEquals(2, report.linked());
        assertEquals(0, report.unmatched());
        assertEquals("TTV-7665-AAAA", report.rows().get(0).inventorySku());
        assertEquals("TTV-7665-BBBB", report.rows().get(1).inventorySku());
        assertEquals("111", saved.get(0).getExternalId());
        assertEquals("222", saved.get(1).getExternalId());
        assertEquals("ACTIVE", saved.get(0).getBricklinkStatus());
        verify(items, never()).save(any());
    }

    @Test
    void leavesExtraLotsUnmatchedAndDoesNotCreateItems() {
        InventoryItem only = inStock("7665", "TTV-7665-AAAA", ItemCondition.NEW_SEALED, Instant.parse("2026-01-01T00:00:00Z"));
        when(items.findWithCatalogByStockStatus(StockStatus.IN_STOCK)).thenReturn(List.of(only));
        when(brickLink.listActiveSetInventories()).thenReturn(List.of(
                lot("111", "7665-1", null, "N", "S", 1),
                lot("222", "7665-1", null, "N", "S", 1)
        ));

        BrickLinkListingImportService.Report report = service.run(false);

        assertEquals(1, report.linked());
        assertEquals(1, report.unmatched());
        assertEquals("UNMATCHED", report.rows().get(1).action());
        assertEquals(1, saved.size());
        verify(items, never()).save(any());
    }

    @Test
    void prefersRemarksSkuOverSetMatch() {
        InventoryItem first = inStock("7665", "TTV-7665-AAAA", ItemCondition.NEW_SEALED, Instant.parse("2026-01-01T00:00:00Z"));
        InventoryItem second = inStock("7665", "TTV-7665-BBBB", ItemCondition.NEW_SEALED, Instant.parse("2026-01-02T00:00:00Z"));
        when(items.findWithCatalogByStockStatus(StockStatus.IN_STOCK)).thenReturn(List.of(first, second));
        when(brickLink.listActiveSetInventories()).thenReturn(List.of(
                lot("222", "7665-1", "TTV-7665-BBBB", "N", "S", 1)
        ));

        BrickLinkListingImportService.Report report = service.run(false);

        assertEquals("TTV-7665-BBBB", report.rows().getFirst().inventorySku());
        assertEquals("Linked existing SKU", report.rows().getFirst().message());
    }

    @Test
    void skipsLotsAlreadyLinkedAndDoesNotReuseThatItem() {
        InventoryItem linked = inStock("7665", "TTV-7665-AAAA", ItemCondition.NEW_SEALED, Instant.parse("2026-01-01T00:00:00Z"));
        InventoryItem free = inStock("7665", "TTV-7665-BBBB", ItemCondition.NEW_SEALED, Instant.parse("2026-01-02T00:00:00Z"));
        ChannelListing existing = ChannelListing.create(linked, Platform.BRICKLINK);
        existing.markPublished("111", BrickLinkPublisher.listingUrl("111"), new BigDecimal("100"));
        when(items.findWithCatalogByStockStatus(StockStatus.IN_STOCK)).thenReturn(List.of(linked, free));
        when(listings.findByPlatformAndExternalId(Platform.BRICKLINK, "111")).thenReturn(Optional.of(existing));
        when(listings.findByInventoryItemIdAndPlatform(linked.getId(), Platform.BRICKLINK)).thenReturn(Optional.of(existing));
        when(brickLink.listActiveSetInventories()).thenReturn(List.of(
                lot("111", "7665-1", null, "N", "S", 1),
                lot("222", "7665-1", null, "N", "S", 1)
        ));

        BrickLinkListingImportService.Report report = service.run(false);

        assertEquals(1, report.skipped());
        assertEquals(1, report.linked());
        assertEquals("TTV-7665-BBBB", report.rows().get(1).inventorySku());
    }

    @Test
    void doesNotMatchSoldItemsOrDifferentSetVariants() {
        InventoryItem sold = inStock("7665", "TTV-7665-SOLD", ItemCondition.NEW_SEALED, Instant.parse("2026-01-01T00:00:00Z"));
        sold.applyStockAndQuantity(StockStatus.SOLD, 0);
        InventoryItem otherVariant = inStock("7665-2", "TTV-7665-2-AAAA", ItemCondition.NEW_SEALED, Instant.parse("2026-01-02T00:00:00Z"));
        when(items.findWithCatalogByStockStatus(StockStatus.IN_STOCK)).thenReturn(List.of(otherVariant));
        when(brickLink.listActiveSetInventories()).thenReturn(List.of(
                lot("111", "7665-1", "TTV-7665-SOLD", "N", "S", 1)
        ));

        BrickLinkListingImportService.Report report = service.run(true);

        assertEquals(1, report.unmatched());
        assertTrue(saved.isEmpty());
    }

    @Test
    void treatsBareSetNumberAsDefaultDashOneVariant() {
        assertTrue(BrickLinkListingImportService.sameSet("7665", "7665-1"));
        assertTrue(BrickLinkListingImportService.sameSet("7665-1", "7665"));
        assertFalse(BrickLinkListingImportService.sameSet("7665-1", "7665-2"));
    }

    private static InventoryItem inStock(String setNumber, String sku, ItemCondition condition, Instant createdAt) {
        CatalogItem catalog = CatalogItem.create(setNumber);
        catalog.setName(setNumber);
        InventoryItem item = InventoryItem.create(catalog, sku);
        item.setTitle(setNumber);
        item.setCondition(condition);
        item.setBricklinkPrice(new BigDecimal("100.00"));
        item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);
        try {
            var field = InventoryItem.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(item, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return item;
    }

    private static BrickLinkActiveListing lot(
            String inventoryId,
            String setNumber,
            String remarks,
            String newOrUsed,
            String completeness,
            int quantity
    ) {
        return new BrickLinkActiveListing(
                inventoryId,
                setNumber,
                setNumber,
                quantity,
                new BigDecimal("199.99"),
                newOrUsed,
                completeness,
                remarks
        );
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
