package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inbound.ShippingCarrier;
import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDeliverySyncServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderService orderService;
    @Mock CarrierTrackingClient tracking;

    OrderDeliverySyncService service;

    @BeforeEach
    void setUp() {
        service = new OrderDeliverySyncService(orders, orderService, tracking);
    }

    @Test
    void marksShippedUpsOrdersDeliveredWhenTheCarrierSaysSo() {
        Order order = shipped("UPS", "1Z999AA10123456784");
        when(orders.findByStatusInAndTrackingNumberIsNotNull(List.of(OrderStatus.OPEN, OrderStatus.SHIPPED)))
                .thenReturn(List.of(order));
        when(tracking.track(ShippingCarrier.UPS, "1Z999AA10123456784"))
                .thenReturn(new CarrierTrackingClient.Snapshot(true, LocalDate.of(2026, 8, 27)));

        assertEquals(1, service.syncDeliveredShipments());
        verify(orderService).markDeliveredFromCarrier(order.getId());
    }

    @Test
    void infersUpsFromTrackingWhenProviderIsBlank() {
        Order order = shipped(null, "1Z999AA10123456784");
        when(orders.findByStatusInAndTrackingNumberIsNotNull(List.of(OrderStatus.OPEN, OrderStatus.SHIPPED)))
                .thenReturn(List.of(order));
        when(tracking.track(ShippingCarrier.UPS, "1Z999AA10123456784"))
                .thenReturn(new CarrierTrackingClient.Snapshot(true, null));

        assertEquals(1, service.syncDeliveredShipments());
        verify(orderService).markDeliveredFromCarrier(order.getId());
    }

    @Test
    void skipsWhenCarrierHasNotDeliveredYet() {
        Order order = shipped("USPS", "9400111899223197428490");
        when(orders.findByStatusInAndTrackingNumberIsNotNull(List.of(OrderStatus.OPEN, OrderStatus.SHIPPED)))
                .thenReturn(List.of(order));
        when(tracking.track(ShippingCarrier.USPS, "9400111899223197428490"))
                .thenReturn(new CarrierTrackingClient.Snapshot(false, LocalDate.of(2026, 8, 30)));

        assertEquals(0, service.syncDeliveredShipments());
        verify(orderService, never()).markDeliveredFromCarrier(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void waitsUntilEveryTrackablePackageIsDelivered() {
        Order order = shipped("UPS", "1ZAAA");
        order.replaceTrackings(List.of(
                ShipmentTracking.of("1ZAAA", "UPS"),
                ShipmentTracking.of("9400111", "USPS")
        ));
        when(orders.findByStatusInAndTrackingNumberIsNotNull(List.of(OrderStatus.OPEN, OrderStatus.SHIPPED)))
                .thenReturn(List.of(order));
        when(tracking.track(ShippingCarrier.UPS, "1ZAAA"))
                .thenReturn(new CarrierTrackingClient.Snapshot(true, null));
        when(tracking.track(ShippingCarrier.USPS, "9400111"))
                .thenReturn(new CarrierTrackingClient.Snapshot(false, null));

        assertEquals(0, service.syncDeliveredShipments());
        verify(orderService, never()).markDeliveredFromCarrier(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void skipsCarriersWithoutATrackingApi() {
        Order order = shipped("DHL", "123456");
        when(orders.findByStatusInAndTrackingNumberIsNotNull(List.of(OrderStatus.OPEN, OrderStatus.SHIPPED)))
                .thenReturn(List.of(order));

        assertEquals(0, service.syncDeliveredShipments());
        verify(orderService, never()).markDeliveredFromCarrier(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    private static Order shipped(String provider, String tracking) {
        return Order.create(null, new ChannelOrder(
                Platform.EBAY,
                "12-345",
                "li-1",
                "TTV-1",
                null,
                "Falcon",
                "75192-1",
                1,
                new BigDecimal("10"),
                "USD",
                Instant.parse("2026-08-20T12:00:00Z"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderStatus.SHIPPED,
                tracking,
                provider
        ), false);
    }
}
