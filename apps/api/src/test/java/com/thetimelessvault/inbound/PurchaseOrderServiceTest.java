package com.thetimelessvault.inbound;

import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.inventory.ChannelPrices;
import com.thetimelessvault.inventory.InventoryDtos;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.publish.PublishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderServiceTest {

    @Mock PurchaseOrderRepository orders;
    @Mock SupplierRepository suppliers;
    @Mock InventoryService inventory;
    @Mock InventoryItemRepository items;
    @Mock CatalogService catalogService;
    @Mock BrickLinkClient brickLinkClient;
    @Mock PublishService publishService;
    @Mock BuyingOpportunityService opportunities;

    PurchaseOrderService service;
    Supplier supplier;
    PurchaseOrder current;
    Map<UUID, InventoryItem> createdItems;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(
                orders, suppliers, inventory, items, catalogService, brickLinkClient, publishService, opportunities);
        supplier = Supplier.create("Brick Depot");
        createdItems = new HashMap<>();
        when(orders.nextPoNumber()).thenReturn(7L);
        when(suppliers.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            current = invocation.getArgument(0);
            return current;
        });
        when(orders.saveAndFlush(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            current = invocation.getArgument(0);
            return current;
        });
        when(orders.findWithDetailsById(any())).thenAnswer(invocation -> Optional.ofNullable(current));
        when(catalogService.lookup(anyString(), eq(false))).thenAnswer(invocation -> catalog(invocation.getArgument(0)));
        when(inventory.create(any())).thenAnswer(invocation -> {
            InventoryDtos.CreateRequest request = invocation.getArgument(0);
            InventoryItem item = InventoryItem.create(catalog(request.setNumber()), "TTV-" + request.setNumber() + "-TEST");
            item.setTitle(request.title());
            item.setCost(request.cost());
            item.applyStockAndQuantity(request.stockStatus(), request.quantity());
            createdItems.put(item.getId(), item);
            return item;
        });
        when(inventory.get(any())).thenAnswer(invocation -> createdItems.get(invocation.getArgument(0)));
        when(items.existsById(any())).thenAnswer(invocation -> createdItems.containsKey(invocation.getArgument(0)));
        when(items.findById(any())).thenAnswer(invocation -> Optional.ofNullable(createdItems.get(invocation.getArgument(0))));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            createdItems.remove(invocation.getArgument(0));
            return null;
        }).when(inventory).delete(any());
    }

    @Test
    void createMakesInTransitInventoryForEachLine() {
        PurchaseOrder order = service.create(request(
                line(null, "75192-1", "Custom Falcon", 2, "100.00"),
                line(null, "10236-1", "Custom Tower", 1, "50.00")
        ));

        assertEquals("PO-7", order.displayNumber());
        assertEquals(PurchaseOrderStatus.IN_TRANSIT, order.getStatus());
        assertEquals(2, order.getLines().size());
        assertEquals(new BigDecimal("250.00"), order.totalValue());

        PurchaseOrderLine first = order.getLines().get(0);
        assertEquals("75192-1", first.getSetNumber());
        assertEquals("Custom Falcon", first.getTitle());
        assertEquals(2, first.getQuantity());
        assertEquals(new BigDecimal("100.00"), first.getUnitValue());
        assertNotNull(first.getInventoryItemId());
        InventoryItem firstItem = createdItems.get(first.getInventoryItemId());
        assertEquals(StockStatus.IN_TRANSIT, firstItem.getStockStatus());
        assertEquals(0, firstItem.getQuantity());
        assertEquals(new BigDecimal("100.00"), firstItem.getCost());
        assertEquals("Custom Falcon", firstItem.getTitle());

        ArgumentCaptor<InventoryDtos.CreateRequest> captor = ArgumentCaptor.forClass(InventoryDtos.CreateRequest.class);
        verify(inventory, times(2)).create(captor.capture());
        InventoryDtos.CreateRequest created = captor.getAllValues().get(0);
        assertEquals(ItemCondition.NEW_SEALED, created.condition());
        assertEquals(StockStatus.IN_TRANSIT, created.stockStatus());
        assertEquals(new BigDecimal("100.00"), created.cost());
        assertEquals(ChannelPrices.ebay(new BigDecimal("100.00")), created.ebayPrice());
        assertEquals(ChannelPrices.bricklink(new BigDecimal("100.00")), created.bricklinkPrice());
        assertEquals(ChannelPrices.shopify(new BigDecimal("100.00")), created.shopifyPrice());
        assertEquals(ChannelPrices.brickowl(new BigDecimal("100.00")), created.brickowlPrice());
        assertTrue(created.shortDescription().contains("Ask for more photos!"));
        assertTrue(created.shortDescription().contains("Box Grade"));
        assertFalse(created.shortDescription().startsWith("Set number:"));
        assertFalse(created.shortDescription().contains("Released"));
    }

    @Test
    void twoLinesWithTheSameSetGetTwoSkus() {
        PurchaseOrder order = service.create(request(
                line(null, "75192-1", "Falcon A", 1, "100.00"),
                line(null, "75192-1", "Falcon B", 1, "110.00")
        ));

        assertNotEquals(order.getLines().get(0).getInventoryItemId(), order.getLines().get(1).getInventoryItemId());
        verify(inventory, times(2)).create(any());
    }

    @Test
    void addingRemovingAndChangingLinesUpdatesInventory() {
        PurchaseOrder order = service.create(request(
                line(null, "75192-1", "Falcon", 1, "100.00"),
                line(null, "10236-1", "Tower", 1, "50.00")
        ));
        PurchaseOrderLine keep = order.getLines().get(0);
        PurchaseOrderLine drop = order.getLines().get(1);
        UUID droppedItem = drop.getInventoryItemId();

        service.update(order.getId(), request(
                line(keep.getId(), keep.getSetNumber(), keep.getTitle(), keep.getQuantity(), "100.00")
        ));
        verify(inventory).delete(droppedItem);
        assertEquals(1, order.getLines().size());

        service.update(order.getId(), request(
                line(keep.getId(), keep.getSetNumber(), keep.getTitle(), keep.getQuantity(), "100.00"),
                line(null, "10236-1", "Tower again", 1, "55.00")
        ));
        verify(inventory, times(3)).create(any());
        PurchaseOrderLine added = order.getLines().get(1);

        UUID previousItem = keep.getInventoryItemId();
        service.update(order.getId(), request(
                line(keep.getId(), "10236-1", "Now a tower", 2, "75.00"),
                line(added.getId(), added.getSetNumber(), added.getTitle(), added.getQuantity(), "55.00")
        ));
        verify(inventory).delete(previousItem);
        verify(inventory, times(4)).create(any());
        verify(publishService).deleteChannelListingIfPresent(previousItem, Platform.SHOPIFY);
        verify(publishService).deleteChannelListingIfPresent(previousItem, Platform.BRICKLINK);
        verify(publishService).deleteChannelListingIfPresent(previousItem, Platform.BRICKOWL);
        verify(publishService).deleteChannelListingIfPresent(previousItem, Platform.EBAY);
        assertEquals("10236-1", keep.getSetNumber());
        assertEquals(2, keep.getQuantity());
        assertNotEquals(previousItem, keep.getInventoryItemId());

        UUID currentItem = keep.getInventoryItemId();
        service.update(order.getId(), request(
                line(keep.getId(), keep.getSetNumber(), "Updated title", 3, "80.00"),
                line(added.getId(), added.getSetNumber(), added.getTitle(), added.getQuantity(), "55.00")
        ));
        ArgumentCaptor<InventoryDtos.UpdateRequest> captor = ArgumentCaptor.forClass(InventoryDtos.UpdateRequest.class);
        verify(inventory).update(eq(currentItem), captor.capture());
        assertEquals("Updated title", captor.getValue().title());
        assertEquals(3, captor.getValue().quantity());
        assertEquals(new BigDecimal("80.00"), captor.getValue().cost());
        assertEquals(StockStatus.IN_TRANSIT, captor.getValue().stockStatus());
    }

    @Test
    void receivePutsItemsInStockWithLineQuantitiesAndLocksEdits() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 4, "100.00")));

        PurchaseOrder received = service.receive(order.getId());
        assertEquals(PurchaseOrderStatus.RECEIVED, received.getStatus());
        InventoryItem item = createdItems.get(order.getLines().get(0).getInventoryItemId());
        assertEquals(StockStatus.IN_STOCK, item.getStockStatus());
        assertEquals(4, item.getQuantity());
        assertEquals(new BigDecimal("100.00"), item.getCost());

        ApiException error = assertThrows(ApiException.class, () -> service.update(order.getId(), request(
                line(order.getLines().get(0).getId(), "75192-1", "Falcon", 4, "100.00")
        )));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void deliverLeavesInventoryInTransitUntilReceived() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 2, "100.00")));
        UUID itemId = order.getLines().get(0).getInventoryItemId();

        service.markDelivered(order.getId());
        verify(opportunities, times(1)).recordPurchaseOrderDelivered(any(), any());

        PurchaseOrder again = service.markDelivered(order.getId());
        assertEquals(PurchaseOrderStatus.DELIVERED, again.getStatus());
        verify(opportunities, times(1)).recordPurchaseOrderDelivered(any(), any());
        InventoryItem item = createdItems.get(itemId);
        assertEquals(StockStatus.IN_TRANSIT, item.getStockStatus());
        assertEquals(0, item.getQuantity());

        service.update(order.getId(), request(
                line(order.getLines().get(0).getId(), "75192-1", "Falcon", 2, "100.00")
        ));

        PurchaseOrder received = service.receive(order.getId());
        assertEquals(PurchaseOrderStatus.RECEIVED, received.getStatus());
        assertEquals(StockStatus.IN_STOCK, createdItems.get(itemId).getStockStatus());
        assertEquals(2, createdItems.get(itemId).getQuantity());
    }

    @Test
    void applyExpectedArrivalUpdatesOpenOrdersOnlyWhenTheDateChanges() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 1, "100.00")));
        LocalDate arrival = LocalDate.of(2026, 8, 30);

        PurchaseOrder updated = service.applyExpectedArrival(order.getId(), arrival);
        assertEquals(arrival, updated.getExpectedArrival());

        PurchaseOrder same = service.applyExpectedArrival(order.getId(), arrival);
        assertEquals(arrival, same.getExpectedArrival());

        service.receive(order.getId());
        PurchaseOrder received = service.applyExpectedArrival(order.getId(), LocalDate.of(2026, 9, 1));
        assertEquals(arrival, received.getExpectedArrival());
    }

    @Test
    void cancelStillWorksAfterDelivered() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 1, "100.00")));
        UUID itemId = order.getLines().get(0).getInventoryItemId();
        service.markDelivered(order.getId());

        PurchaseOrder cancelled = service.cancel(order.getId());
        assertEquals(PurchaseOrderStatus.CANCELLED, cancelled.getStatus());
        verify(inventory).delete(itemId);
    }

    @Test
    void cancelUnlistsAndDeletesLinkedSkus() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 1, "100.00")));
        UUID itemId = order.getLines().get(0).getInventoryItemId();

        PurchaseOrder cancelled = service.cancel(order.getId());
        assertEquals(PurchaseOrderStatus.CANCELLED, cancelled.getStatus());
        assertNull(order.getLines().get(0).getInventoryItemId());

        InOrder sequence = inOrder(publishService, inventory);
        sequence.verify(publishService).deactivatePublishedListings(itemId);
        sequence.verify(publishService).deleteChannelListingIfPresent(itemId, Platform.SHOPIFY);
        sequence.verify(publishService).deleteChannelListingIfPresent(itemId, Platform.BRICKLINK);
        sequence.verify(publishService).deleteChannelListingIfPresent(itemId, Platform.BRICKOWL);
        sequence.verify(publishService).deleteChannelListingIfPresent(itemId, Platform.EBAY);
        sequence.verify(inventory).delete(itemId);
        assertNull(createdItems.get(itemId));
    }

    @Test
    void listIsNewestCreatedFirst() {
        when(orders.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(any(Specification.class), captor.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), captor.getValue().getSort());
    }

    @Test
    void headerCanBeEditedWithoutChangingLines() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 1, "100.00")));
        UUID sku = order.getLines().get(0).getInventoryItemId();

        service.updateHeader(order.getId(), new PurchaseOrderService.HeaderRequest(
                LocalDate.parse("2026-09-01"),
                "1Z999",
                ShippingCarrier.UPS,
                null
        ));

        assertEquals(LocalDate.parse("2026-09-01"), order.getExpectedArrival());
        assertEquals("1Z999", order.getTrackingNumber());
        assertEquals(ShippingCarrier.UPS, order.getCarrier());
        assertEquals(sku, order.getLines().get(0).getInventoryItemId());
    }

    @Test
    void receivedOrdersStillAllowHeaderEdits() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 1, "100.00")));
        service.receive(order.getId());

        service.updateHeader(order.getId(), new PurchaseOrderService.HeaderRequest(
                LocalDate.parse("2026-09-02"),
                "9400",
                ShippingCarrier.USPS,
                null
        ));

        assertEquals("9400", order.getTrackingNumber());
        assertEquals(ShippingCarrier.USPS, order.getCarrier());
    }

    @Test
    void cancelledOrdersRejectHeaderEdits() {
        PurchaseOrder order = service.create(request(line(null, "75192-1", "Falcon", 1, "100.00")));
        service.cancel(order.getId());

        ApiException error = assertThrows(ApiException.class, () -> service.updateHeader(
                order.getId(),
                new PurchaseOrderService.HeaderRequest(null, "1Z999", ShippingCarrier.UPS, null)
        ));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    private PurchaseOrderService.UpsertRequest request(PurchaseOrderService.LineRequest... lines) {
        return new PurchaseOrderService.UpsertRequest(supplier.getId(), null, null, null, null, List.of(lines), null);
    }

    private static PurchaseOrderService.LineRequest line(UUID id, String set, String title, int qty, String value) {
        return new PurchaseOrderService.LineRequest(id, set, title, qty, new BigDecimal(value));
    }

    private static CatalogItem catalog(String setNumber) {
        CatalogItem catalog = CatalogItem.create(setNumber);
        catalog.setName(setNumber.contains("10236") ? "Eiffel Tower" : "Millennium Falcon");
        catalog.setTheme("Icons");
        return catalog;
    }
}
