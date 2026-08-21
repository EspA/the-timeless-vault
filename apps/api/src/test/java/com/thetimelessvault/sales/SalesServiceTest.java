package com.thetimelessvault.sales;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.publish.PublishService;
import com.thetimelessvault.settings.ChannelFeeRates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock SaleRepository sales;
    @Mock SaleIgnoreRepository ignores;
    @Mock InventoryItemRepository items;
    @Mock ChannelListingRepository listings;
    @Mock CatalogService catalogService;
    @Mock AppSettingRepository settings;
    @Mock PublishService publishService;

    SalesService service;
    CatalogItem catalog;
    InventoryItem existing;

    @BeforeEach
    void setUp() {
        service = new SalesService(
                sales, ignores, items, listings, catalogService, settings, publishService, new ChannelFeeRates(settings));
        catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        existing = InventoryItem.create(catalog, "TTV-75192-1-AAAA");
        existing.applyStockAndQuantity(StockStatus.IN_STOCK, 1);
        lenient().when(sales.findByPlatformAndExternalOrderIdAndExternalLineId(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void matchesExistingSkuAndMarksItemSold() {
        ChannelSale incoming = sale("TTV-75192-1-AAAA", null);
        when(sales.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importSale(incoming));
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        assertEquals(0, existing.getQuantity());
        verify(catalogService, never()).lookupOrStub(any(), any());
        ArgumentCaptor<Sale> captor = ArgumentCaptor.forClass(Sale.class);
        verify(sales).save(captor.capture());
        assertFalse(captor.getValue().isInventoryCreated());
        assertEquals(existing.getId(), captor.getValue().getInventoryItemId());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.EBAY);
        verify(items).save(existing);
    }

    @Test
    void matchesEbayListingUrlWhenSkuIsMissing() {
        ChannelListing listing = ChannelListing.create(existing, Platform.EBAY);
        listing.markPublished("offer-1", "https://www.ebay.com/itm/333", new BigDecimal("899.99"));
        ChannelSale incoming = sale(null, "333");
        when(sales.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(listings.findByPlatformAndExternalId(Platform.EBAY, "333")).thenReturn(Optional.empty());
        when(listings.findByPlatformAndLiveUrl(eq(Platform.EBAY), eq("https://www.ebay.com/itm/333")))
                .thenReturn(Optional.of(listing));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importSale(incoming));
        verify(sales).save(any());
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        assertEquals(0, existing.getQuantity());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.EBAY);
        verify(items).save(existing);
    }

    @Test
    void createsSoldItemWhenMissingFromStock() {
        ChannelSale incoming = sale("TTV-75192-1-ZZZZ", null);
        when(sales.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-ZZZZ")).thenReturn(Optional.empty());
        when(catalogService.lookupOrStub("75192-1", incoming.title())).thenReturn(catalog);
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importSale(incoming));

        ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(items).save(itemCaptor.capture());
        assertEquals(StockStatus.SOLD, itemCaptor.getValue().getStockStatus());
        assertEquals(0, itemCaptor.getValue().getQuantity());
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
        verify(sales).save(saleCaptor.capture());
        assertTrue(saleCaptor.getValue().isInventoryCreated());
        verify(publishService, never()).deactivatePublishedListingsAfterSale(any(), any());
    }

    @Test
    void skipsDuplicateChannelOrderLines() {
        Sale existingSale = Sale.create(existing, sale("TTV-75192-1-AAAA", null), false);
        when(sales.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingSale));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importSale(sale("TTV-75192-1-AAAA", null)));
        verify(items, never()).save(any());
        verify(publishService, never()).deactivatePublishedListingsAfterSale(any(), any());
    }

    @Test
    void addManualCreatesSaleAndMatchesExistingSku() {
        when(sales.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.BRICKLINK, "BL-99", "manual"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Sale saved = service.addManual(new SalesService.ManualSaleRequest(
                Platform.BRICKLINK,
                "TTV-75192-1-AAAA",
                null,
                null,
                "BL-99",
                null,
                1,
                new BigDecimal("120.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z")
        ));

        assertEquals(existing.getId(), saved.getInventoryItemId());
        assertEquals("BL-99", saved.getExternalOrderId());
        assertEquals(0, new BigDecimal("6.48").compareTo(saved.getPlatformFee()));
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        assertEquals(0, existing.getQuantity());
        assertFalse(saved.isInventoryCreated());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.BRICKLINK);
    }

    @Test
    void addManualAcceptsLocalChannel() {
        when(sales.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.LOCAL, "walk-in", "manual"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Sale saved = service.addManual(new SalesService.ManualSaleRequest(
                Platform.LOCAL,
                "TTV-75192-1-AAAA",
                null,
                null,
                "walk-in",
                null,
                1,
                new BigDecimal("250.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z")
        ));

        assertEquals(Platform.LOCAL, saved.getPlatform());
        assertEquals("walk-in", saved.getExternalOrderId());
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.LOCAL);
    }

    @Test
    void addManualRequiresAnItemIdentity() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.thetimelessvault.common.ApiException.class,
                () -> service.addManual(new SalesService.ManualSaleRequest(
                        Platform.EBAY, null, null, null, "1", null, 1, new BigDecimal("10"), "USD", Instant.now()
                ))
        );
        verify(sales, never()).save(any());
    }

    @Test
    void deleteRemovesSaleAndIgnoresChannelOrder() {
        ChannelSale incoming = sale("TTV-75192-1-AAAA", null);
        Sale existingSale = Sale.create(existing, incoming, false);
        when(sales.findById(existingSale.getId())).thenReturn(Optional.of(existingSale));
        when(ignores.existsByPlatformAndExternalOrderIdAndExternalLineId(
                Platform.EBAY, "12-345", "li-1")).thenReturn(false);

        service.delete(existingSale.getId());

        verify(ignores).save(any());
        verify(sales).delete(existingSale);
    }

    @Test
    void importSaleSkipsIgnoredChannelOrders() {
        when(ignores.existsByPlatformAndExternalOrderIdAndExternalLineId(
                Platform.EBAY, "12-345", "li-1")).thenReturn(true);

        assertFalse(service.importSale(sale("TTV-75192-1-AAAA", null)));
        verify(sales, never()).save(any());
        verify(items, never()).save(any());
    }

    @Test
    void deleteMissingSaleThrows() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(sales.findById(id)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.thetimelessvault.common.ApiException.class,
                () -> service.delete(id)
        );
        verify(sales, never()).delete(any());
        verify(ignores, never()).save(any());
    }

    private static ChannelSale sale(String sku, String listingId) {
        return new ChannelSale(
                Platform.EBAY,
                "12-345",
                "li-1",
                sku,
                listingId,
                "LEGO 75192 Millennium Falcon",
                "75192-1",
                1,
                new BigDecimal("899.99"),
                "USD",
                Instant.parse("2026-08-20T12:00:00Z"),
                "https://www.ebay.com/sh/ord/details?orderid=12-345",
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
