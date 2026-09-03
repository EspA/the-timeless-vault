package com.thetimelessvault.inbound;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        when(orders.findByStatusInAndTrackingNumberIsNotNull(
                List.of(PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELIVERED)
        )).thenReturn(List.of(order));
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
        when(orders.findByStatusInAndTrackingNumberIsNotNull(
                List.of(PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELIVERED)
        )).thenReturn(List.of(order));
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
        when(orders.findByStatusInAndTrackingNumberIsNotNull(
                List.of(PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELIVERED)
        )).thenReturn(List.of(order));

        assertEquals(0, service.syncDeliveredShipments());
        verify(purchaseOrders, never()).markDelivered(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void syncViewRefreshesOneOrderFromTheCarrier() {
        PurchaseOrder order = PurchaseOrder.create(supplier, 8);
        order.setCarrier(ShippingCarrier.USPS);
        order.setTrackingNumber("9400111899223197428490");
        LocalDate eta = LocalDate.of(2026, 8, 29);
        when(purchaseOrders.get(order.getId())).thenReturn(order);
        when(tracking.trackRequired(ShippingCarrier.USPS, "9400111899223197428490"))
                .thenReturn(new CarrierTrackingClient.Snapshot(false, eta));
        when(purchaseOrders.view(order.getId())).thenReturn(view(order, eta));

        PurchaseOrderDtos.PurchaseOrderView result = service.syncView(order.getId());

        assertEquals(eta, result.expectedArrival());
        verify(purchaseOrders).applyExpectedArrival(order.getId(), eta);
        verify(purchaseOrders, never()).markDelivered(any(UUID.class));
    }

    @Test
    void syncViewMarksDeliveredWhenTheCarrierSaysSo() {
        PurchaseOrder order = PurchaseOrder.create(supplier, 7);
        order.setCarrier(ShippingCarrier.UPS);
        order.setTrackingNumber("1Z999AA10123456784");
        when(purchaseOrders.get(order.getId())).thenReturn(order);
        when(tracking.trackRequired(ShippingCarrier.UPS, "1Z999AA10123456784"))
                .thenReturn(new CarrierTrackingClient.Snapshot(true, LocalDate.of(2026, 8, 27)));
        when(purchaseOrders.view(order.getId())).thenReturn(view(order, LocalDate.of(2026, 8, 27)));

        service.syncView(order.getId());

        verify(purchaseOrders).applyExpectedArrival(order.getId(), LocalDate.of(2026, 8, 27));
        verify(purchaseOrders).markDelivered(order.getId());
    }

    @Test
    void syncViewRejectsMissingTracking() {
        PurchaseOrder order = PurchaseOrder.create(supplier, 6);
        order.setCarrier(ShippingCarrier.FEDEX);
        when(purchaseOrders.get(order.getId())).thenReturn(order);

        ApiException error = assertThrows(ApiException.class, () -> service.syncView(order.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(tracking, never()).trackRequired(any(), any());
    }

    private PurchaseOrderDtos.PurchaseOrderView view(PurchaseOrder order, LocalDate expectedArrival) {
        Instant now = Instant.parse("2026-08-27T12:00:00Z");
        return new PurchaseOrderDtos.PurchaseOrderView(
                order.getId(),
                order.displayNumber(),
                supplier.getId(),
                supplier.getName(),
                order.getStatus(),
                BigDecimal.ZERO,
                expectedArrival,
                order.getTrackingNumber(),
                order.getCarrier(),
                List.of(),
                null,
                List.of(),
                now,
                now
        );
    }
}
