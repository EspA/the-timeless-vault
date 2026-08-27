package com.thetimelessvault.orders;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderIgnoreRepository ignores;
    @Mock InventoryItemRepository items;
    @Mock ChannelListingRepository listings;
    @Mock CatalogService catalogService;
    @Mock AppSettingRepository settings;
    @Mock PublishService publishService;
    @Mock BuyingOpportunityService opportunities;

    OrderService service;
    CatalogItem catalog;
    InventoryItem existing;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                orders, ignores, items, listings, catalogService, settings, publishService, opportunities, new ChannelFeeRates(settings));
        catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        existing = InventoryItem.create(catalog, "TTV-75192-1-AAAA");
        existing.applyStockAndQuantity(StockStatus.IN_STOCK, 1);
        lenient().when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void matchesExistingSkuAndMarksItemSold() {
        ChannelOrder incoming = order("TTV-75192-1-AAAA", null, OrderStatus.OPEN);
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importOrder(incoming));
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        assertEquals(0, existing.getQuantity());
        verify(catalogService, never()).lookupOrStub(any(), any());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        assertFalse(captor.getValue().isInventoryCreated());
        assertEquals(existing.getId(), captor.getValue().getInventoryItemId());
        assertEquals(OrderStatus.OPEN, captor.getValue().getStatus());
        assertEquals("UPS", captor.getValue().getShippingProvider());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.EBAY);
        verify(items).save(existing);
        verify(opportunities).recordNewSale(any(), eq(existing));
    }

    @Test
    void firstCancelledImportDoesNotMarkItemSold() {
        ChannelOrder incoming = order("TTV-75192-1-AAAA", null, OrderStatus.CANCELLED);
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importOrder(incoming));
        assertEquals(StockStatus.IN_STOCK, existing.getStockStatus());
        assertEquals(1, existing.getQuantity());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        assertEquals(OrderStatus.CANCELLED, captor.getValue().getStatus());
        verify(publishService, never()).deactivatePublishedListingsAfterSale(any(), any());
        verify(items, never()).save(any());
        verify(opportunities, never()).recordNewSale(any(), any());
    }

    @Test
    void laterCancelledImportRestoresStock() {
        existing.applyStockAndQuantity(StockStatus.SOLD, 0);
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.OPEN), false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.CANCELLED, "1Z999", "UPS")));
        assertEquals(OrderStatus.CANCELLED, existingOrder.getStatus());
        assertEquals("1Z999", existingOrder.getTrackingNumber());
        assertEquals(StockStatus.IN_STOCK, existing.getStockStatus());
        assertEquals(1, existing.getQuantity());
        verify(opportunities, never()).recordNewSale(any(), any());
        verify(publishService, never()).deactivatePublishedListingsAfterSale(any(), any());
    }

    @Test
    void duplicateSyncMovesStatusForwardAndKeepsTracking() {
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.OPEN), false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChannelOrder shipped = order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED, "9400111", "USPS");
        assertFalse(service.importOrder(shipped));
        assertEquals(OrderStatus.SHIPPED, existingOrder.getStatus());
        assertEquals("9400111", existingOrder.getTrackingNumber());
        assertEquals("USPS", existingOrder.getShippingProvider());
        verify(items, never()).save(any());
        verify(opportunities, never()).recordNewSale(any(), any());
        verify(opportunities, never()).recordOrderDelivered(any(), any());
    }

    @Test
    void laterChannelSyncKeepsProviderWhenChannelOmitsIt() {
        Order existingOrder = Order.create(
                existing, order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED, "9400111", "USPS"), false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED, "9400111", null)));
        assertEquals("USPS", existingOrder.getShippingProvider());
    }

    @Test
    void channelDeliveredStatusCreatesNotification() {
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED), false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.COMPLETED, "9400111", "USPS")));
        assertEquals(OrderStatus.COMPLETED, existingOrder.getStatus());
        verify(opportunities).recordOrderDelivered(existingOrder, existing);
        verify(opportunities, never()).recordNewSale(any(), any());
    }

    @Test
    void carrierDeliveredMarksCompletedAndNotifiesWithoutManualOverride() {
        Order existingOrder = Order.create(
                existing, order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED, "1Z999", "UPS"), false);
        when(orders.findById(existingOrder.getId())).thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.markDeliveredFromCarrier(existingOrder.getId());
        assertEquals(OrderStatus.COMPLETED, existingOrder.getStatus());
        assertEquals(OrderStatusSource.CHANNEL, existingOrder.getStatusSource());
        verify(opportunities).recordOrderDelivered(existingOrder, existing);
    }

    @Test
    void alreadyDeliveredSyncDoesNotNotifyAgain() {
        Order existingOrder = Order.create(
                existing, order("TTV-75192-1-AAAA", null, OrderStatus.COMPLETED, "9400111", "USPS"), false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.COMPLETED)));
        verify(opportunities, never()).recordOrderDelivered(any(), any());
    }

    @Test
    void manualDeliveredStatusCreatesNotification() {
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED), false);
        when(orders.findById(existingOrder.getId())).thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findById(existing.getId())).thenReturn(Optional.of(existing));

        Order updated = service.update(existingOrder.getId(), new OrderService.UpdateOrderRequest(
                OrderStatus.COMPLETED, null, null));

        assertEquals(OrderStatus.COMPLETED, updated.getStatus());
        verify(opportunities).recordOrderDelivered(existingOrder, existing);
    }

    @Test
    void migratedCompletedTakesChannelOpenStatus() {
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.OPEN), false);
        existingOrder.markMigrated(OrderStatus.COMPLETED);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.OPEN)));
        assertEquals(OrderStatus.OPEN, existingOrder.getStatus());
        assertEquals(OrderStatusSource.CHANNEL, existingOrder.getStatusSource());
    }

    @Test
    void migratedCompletedTakesChannelShippedAndTracking() {
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.OPEN), false);
        existingOrder.markMigrated(OrderStatus.COMPLETED);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED, "9400111", "USPS")));
        assertEquals(OrderStatus.SHIPPED, existingOrder.getStatus());
        assertEquals("9400111", existingOrder.getTrackingNumber());
        assertEquals("USPS", existingOrder.getShippingProvider());
    }

    @Test
    void syncDoesNotMoveStatusBackward() {
        Order existingOrder = Order.create(
                existing, order("TTV-75192-1-AAAA", null, OrderStatus.COMPLETED, "9400111", "USPS"), false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.SHIPPED)));
        assertEquals(OrderStatus.COMPLETED, existingOrder.getStatus());
        assertEquals("9400111", existingOrder.getTrackingNumber());
    }

    @Test
    void manualStatusCanMoveToCancelledAndRestoreStock() {
        existing.applyStockAndQuantity(StockStatus.SOLD, 0);
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.OPEN), false);
        when(orders.findById(existingOrder.getId())).thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order updated = service.update(existingOrder.getId(), new OrderService.UpdateOrderRequest(
                OrderStatus.CANCELLED, null, null));

        assertEquals(OrderStatus.CANCELLED, updated.getStatus());
        assertEquals(StockStatus.IN_STOCK, existing.getStockStatus());
        assertEquals(1, existing.getQuantity());
    }

    @Test
    void matchesEbayListingUrlWhenSkuIsMissing() {
        ChannelListing listing = ChannelListing.create(existing, Platform.EBAY);
        listing.markPublished("offer-1", "https://www.ebay.com/itm/333", new BigDecimal("899.99"));
        ChannelOrder incoming = order(null, "333", OrderStatus.OPEN);
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(listings.findByPlatformAndExternalId(Platform.EBAY, "333")).thenReturn(Optional.empty());
        when(listings.findByPlatformAndLiveUrl(eq(Platform.EBAY), eq("https://www.ebay.com/itm/333")))
                .thenReturn(Optional.of(listing));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importOrder(incoming));
        verify(orders).save(any());
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        assertEquals(0, existing.getQuantity());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.EBAY);
        verify(items).save(existing);
        verify(opportunities).recordNewSale(any(), eq(existing));
    }

    @Test
    void createsSoldItemWhenMissingFromStock() {
        ChannelOrder incoming = order("TTV-75192-1-ZZZZ", null, OrderStatus.OPEN);
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-ZZZZ")).thenReturn(Optional.empty());
        when(catalogService.lookupOrStub("75192-1", incoming.title())).thenReturn(catalog);
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importOrder(incoming));

        ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(items).save(itemCaptor.capture());
        assertEquals(StockStatus.SOLD, itemCaptor.getValue().getStockStatus());
        assertEquals(0, itemCaptor.getValue().getQuantity());
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(orderCaptor.capture());
        assertTrue(orderCaptor.getValue().isInventoryCreated());
        verify(publishService, never()).deactivatePublishedListingsAfterSale(any(), any());
        verify(opportunities).recordNewSale(any(), any());
    }

    @Test
    void skipsDuplicateChannelOrderLines() {
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.OPEN), false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.OPEN)));
        verify(items, never()).save(any());
        verify(publishService, never()).deactivatePublishedListingsAfterSale(any(), any());
        verify(opportunities, never()).recordNewSale(any(), any());
    }

    @Test
    void addManualCreatesOrderAndMatchesExistingSku() {
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.BRICKLINK, "BL-99", "manual"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order saved = service.addManual(new OrderService.ManualOrderRequest(
                Platform.BRICKLINK,
                "TTV-75192-1-AAAA",
                null,
                null,
                "BL-99",
                null,
                1,
                new BigDecimal("120.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z"),
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(existing.getId(), saved.getInventoryItemId());
        assertEquals("BL-99", saved.getExternalOrderId());
        assertEquals(OrderStatus.OPEN, saved.getStatus());
        assertEquals(0, new BigDecimal("6.48").compareTo(saved.getPlatformFee()));
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        assertEquals(0, existing.getQuantity());
        assertFalse(saved.isInventoryCreated());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.BRICKLINK);
        verify(opportunities).recordNewSale(any(), eq(existing));
    }

    @Test
    void addManualAcceptsLocalChannel() {
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.LOCAL, "walk-in", "manual"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order saved = service.addManual(new OrderService.ManualOrderRequest(
                Platform.LOCAL,
                "TTV-75192-1-AAAA",
                null,
                null,
                "walk-in",
                null,
                1,
                new BigDecimal("250.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z"),
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals(Platform.LOCAL, saved.getPlatform());
        assertEquals("walk-in", saved.getExternalOrderId());
        assertEquals(StockStatus.SOLD, existing.getStockStatus());
        verify(publishService).deactivatePublishedListingsAfterSale(existing.getId(), Platform.LOCAL);
    }

    @Test
    void addManualDefaultsToLocalAndStoresOptionalFulfillment() {
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(eq(Platform.LOCAL), any(), eq("manual")))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order saved = service.addManual(new OrderService.ManualOrderRequest(
                null,
                "TTV-75192-1-AAAA",
                null,
                null,
                null,
                null,
                1,
                new BigDecimal("250.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z"),
                OrderStatus.SHIPPED,
                "1Z14V5340327789307",
                null,
                new BigDecimal("12.50"),
                new BigDecimal("5.00")
        ));

        assertEquals(Platform.LOCAL, saved.getPlatform());
        assertEquals(OrderStatus.SHIPPED, saved.getStatus());
        assertEquals("1Z14V5340327789307", saved.getTrackingNumber());
        assertEquals("UPS", saved.getShippingProvider());
        assertEquals(0, new BigDecimal("12.50").compareTo(saved.getShippingCost()));
        assertEquals(0, new BigDecimal("5.00").compareTo(saved.getPlatformFee()));
        assertTrue(saved.getExternalOrderId().startsWith("MANUAL-"));
    }

    @Test
    void addManualDefaultsShippingProviderToUpsWhenOmitted() {
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(eq(Platform.LOCAL), any(), eq("manual")))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-75192-1-AAAA")).thenReturn(Optional.of(existing));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order saved = service.addManual(new OrderService.ManualOrderRequest(
                null,
                "TTV-75192-1-AAAA",
                null,
                null,
                null,
                null,
                1,
                new BigDecimal("250.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z"),
                OrderStatus.OPEN,
                null,
                null,
                null,
                null
        ));

        assertEquals("UPS", saved.getShippingProvider());
    }

    @Test
    void addManualRequiresAnItemIdentity() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.thetimelessvault.common.ApiException.class,
                () -> service.addManual(new OrderService.ManualOrderRequest(
                        Platform.EBAY, null, null, null, "1", null, 1, new BigDecimal("10"), "USD", Instant.now(),
                        null, null, null, null, null
                ))
        );
        verify(orders, never()).save(any());
    }

    @Test
    void deleteRemovesOrderAndIgnoresChannelOrder() {
        ChannelOrder incoming = order("TTV-75192-1-AAAA", null, OrderStatus.OPEN);
        Order existingOrder = Order.create(existing, incoming, false);
        when(orders.findById(existingOrder.getId())).thenReturn(Optional.of(existingOrder));
        when(ignores.existsByPlatformAndExternalOrderIdAndExternalLineId(
                Platform.EBAY, "12-345", "li-1")).thenReturn(false);

        service.delete(existingOrder.getId());

        verify(ignores).save(any());
        verify(orders).delete(existingOrder);
    }

    @Test
    void importOrderSkipsIgnoredChannelOrders() {
        when(ignores.existsByPlatformAndExternalOrderIdAndExternalLineId(
                Platform.EBAY, "12-345", "li-1")).thenReturn(true);

        assertFalse(service.importOrder(order("TTV-75192-1-AAAA", null, OrderStatus.OPEN)));
        verify(orders, never()).save(any());
        verify(items, never()).save(any());
        verify(opportunities, never()).recordNewSale(any(), any());
    }

    @Test
    void getReturnsExistingOrder() {
        Order existingOrder = Order.create(existing, order("TTV-75192-1-AAAA", null, OrderStatus.OPEN), false);
        when(orders.findById(existingOrder.getId())).thenReturn(Optional.of(existingOrder));

        assertEquals(existingOrder, service.get(existingOrder.getId()));
    }

    @Test
    void getMissingOrderThrows() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(orders.findById(id)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.thetimelessvault.common.ApiException.class,
                () -> service.get(id)
        );
    }

    @Test
    void deleteMissingOrderThrows() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(orders.findById(id)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.thetimelessvault.common.ApiException.class,
                () -> service.delete(id)
        );
        verify(orders, never()).delete(any());
        verify(ignores, never()).save(any());
    }

    @Test
    void brickLinkSyncClearsPulledShippingMethod() {
        ChannelOrder previous = new ChannelOrder(
                Platform.BRICKLINK, "88", "li-1", "TTV-75192-1-AAAA", null,
                "Falcon", "75192-1", 1, new BigDecimal("10"), "USD",
                Instant.parse("2026-08-20T12:00:00Z"), null, BigDecimal.ZERO, BigDecimal.ZERO,
                OrderStatus.OPEN, null, "Request for Invoice");
        Order existingOrder = Order.create(existing, previous, false);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.BRICKLINK, "88", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChannelOrder incoming = new ChannelOrder(
                Platform.BRICKLINK, "88", "li-1", "TTV-75192-1-AAAA", null,
                "Falcon", "75192-1", 1, new BigDecimal("10"), "USD",
                Instant.parse("2026-08-20T12:00:00Z"), null, BigDecimal.ZERO, BigDecimal.ZERO,
                OrderStatus.OPEN, null, null);
        assertFalse(service.importOrder(incoming));
        assertEquals("UPS", existingOrder.getShippingProvider());
    }

    @Test
    void brickLinkSyncClearsPulledShippingMethodAndInfersUps() {
        ChannelOrder previous = new ChannelOrder(
                Platform.BRICKLINK, "88", "li-1", "TTV-75192-1-AAAA", null,
                "Falcon", "75192-1", 1, new BigDecimal("10"), "USD",
                Instant.parse("2026-08-20T12:00:00Z"), null, BigDecimal.ZERO, BigDecimal.ZERO,
                OrderStatus.OPEN, null, "Request for Invoice");
        Order existingOrder = Order.create(existing, previous, false);
        existingOrder.markMigrated(OrderStatus.OPEN);
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.BRICKLINK, "88", "li-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChannelOrder incoming = new ChannelOrder(
                Platform.BRICKLINK, "88", "li-1", "TTV-75192-1-AAAA", null,
                "Falcon", "75192-1", 1, new BigDecimal("10"), "USD",
                Instant.parse("2026-08-20T12:00:00Z"), null, BigDecimal.ZERO, BigDecimal.ZERO,
                OrderStatus.SHIPPED, "1Z14V5340327789307", null);
        assertFalse(service.importOrder(incoming));
        assertEquals("1Z14V5340327789307", existingOrder.getTrackingNumber());
        assertEquals("UPS", existingOrder.getShippingProvider());
    }

    @Test
    void firstCancelledWithoutInventoryLeavesItemNull() {
        ChannelOrder incoming = order("TTV-UNKNOWN", null, OrderStatus.CANCELLED);
        when(orders.existsByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(false);
        when(items.findWithCatalogBySkuIgnoreCase("TTV-UNKNOWN")).thenReturn(Optional.empty());
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.importOrder(incoming));
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        assertNull(captor.getValue().getInventoryItemId());
        verify(catalogService, never()).lookupOrStub(any(), any());
        verify(opportunities, never()).recordNewSale(any(), any());
    }

    private static ChannelOrder order(String sku, String listingId, OrderStatus status) {
        return order(sku, listingId, status, null, null);
    }

    private static ChannelOrder order(
            String sku,
            String listingId,
            OrderStatus status,
            String tracking,
            String provider
    ) {
        return new ChannelOrder(
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
                BigDecimal.ZERO,
                status,
                tracking,
                provider
        );
    }
}
