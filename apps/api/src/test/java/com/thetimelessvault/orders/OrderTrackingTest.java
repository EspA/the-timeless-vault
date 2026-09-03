package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderTrackingTest {

    @Test
    void replaceTrackingsKeepsUniqueNumbersAndSyncsThePrimary() {
        Order order = Order.create(null, incoming("1ZAAA", "UPS"), false);

        order.replaceTrackings(List.of(
                ShipmentTracking.of("9400111", "USPS"),
                ShipmentTracking.of("1ZBBB", "UPS"),
                ShipmentTracking.of("9400111", "USPS")
        ));

        assertEquals(2, order.getTrackings().size());
        assertEquals("9400111", order.getTrackingNumber());
        assertEquals("USPS", order.getShippingProvider());
        assertEquals("1ZBBB", order.getTrackings().get(1).getTrackingNumber());
    }

    @Test
    void channelSyncAddsANewTrackingWithoutDroppingManualOnes() {
        Order order = Order.create(null, incoming("1ZAAA", "UPS"), false);
        order.replaceTrackings(List.of(
                ShipmentTracking.of("1ZAAA", "UPS"),
                ShipmentTracking.of("9400111", "USPS")
        ));

        order.applyChannelUpdate(incoming("1ZCCC", "UPS"));

        assertEquals(3, order.getTrackings().size());
        assertEquals("1ZAAA", order.getTrackingNumber());
    }

    @Test
    void clearingTrackingsRemovesThePrimaryNumber() {
        Order order = Order.create(null, incoming("1ZAAA", "UPS"), false);
        order.replaceTrackings(List.of());

        assertNull(order.getTrackingNumber());
        assertEquals("UPS", order.getShippingProvider());
        assertEquals(0, order.getTrackings().size());
    }

    private static ChannelOrder incoming(String tracking, String provider) {
        return new ChannelOrder(
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
        );
    }
}
