package com.thetimelessvault.inbound;

import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderDeliverySyncServiceTest {

    @Mock PurchaseOrderRepository orders;
    @Mock PurchaseOrderService purchaseOrders;
    @Mock CarrierTrackingClient tracking;

    PurchaseOrderDeliverySyncService service;
    Supplier supplier;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderDeliverySyncService(orders, purchaseOrders, tracking);
        supplier = Supplier.create("Brick Depot");
    }

    @Test
    void marksInTransitUpsShipmentsDeliveredWhenTheCarrierSaysSo() {
        PurchaseOrder order = PurchaseOrder.create(supplier, 9);
        order.setCarrier(ShippingCarrier.UPS);
        order.setTrackingNumber("1Z999AA10123456784");
        when(orders.findByStatusAndTrackingNumberIsNotNull(PurchaseOrderStatus.IN_TRANSIT)).thenReturn(List.of(order));
        when(tracking.track(ShippingCarrier.UPS, "1Z999AA10123456784"))
                .thenReturn(new CarrierTrackingClient.Snapshot(true, LocalDate.of(2026, 8, 27)));

        assertEquals(1, service.syncDeliveredShipments());
        verify(purchaseOrders).applyExpectedArrival(order.getId(), LocalDate.of(2026, 8, 27));
        verify(purchaseOrders).markDelivered(order.getId());
    }

    @Test
    void updatesExpectedArrivalWithoutMarkingDelivered() {
        PurchaseOrder order = PurchaseOrder.create(supplier, 11);
        order.setCarrier(ShippingCarrier.FEDEX);
        order.setTrackingNumber("123456789012");
        when(orders.findByStatusAndTrackingNumberIsNotNull(PurchaseOrderStatus.IN_TRANSIT)).thenReturn(List.of(order));
        when(tracking.track(ShippingCarrier.FEDEX, "123456789012"))
                .thenReturn(new CarrierTrackingClient.Snapshot(false, LocalDate.of(2026, 8, 30)));

        assertEquals(0, service.syncDeliveredShipments());
        verify(purchaseOrders).applyExpectedArrival(order.getId(), LocalDate.of(2026, 8, 30));
        verify(purchaseOrders, never()).markDelivered(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void skipsCarriersWithoutATrackingApi() {
        PurchaseOrder order = PurchaseOrder.create(supplier, 10);
        order.setCarrier(ShippingCarrier.DHL);
        order.setTrackingNumber("123456");
        when(orders.findByStatusAndTrackingNumberIsNotNull(PurchaseOrderStatus.IN_TRANSIT)).thenReturn(List.of(order));

        assertEquals(0, service.syncDeliveredShipments());
        verify(purchaseOrders, never()).markDelivered(org.mockito.ArgumentMatchers.any(UUID.class));
    }
}
