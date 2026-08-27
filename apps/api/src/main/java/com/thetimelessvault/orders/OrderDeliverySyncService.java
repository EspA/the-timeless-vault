package com.thetimelessvault.orders;

import com.thetimelessvault.inbound.ShippingCarrier;
import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderDeliverySyncService {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliverySyncService.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final CarrierTrackingClient tracking;

    public OrderDeliverySyncService(
            OrderRepository orders,
            OrderService orderService,
            CarrierTrackingClient tracking
    ) {
        this.orders = orders;
        this.orderService = orderService;
        this.tracking = tracking;
    }

    public int syncDeliveredShipments() {
        List<Order> open = orders.findByStatusInAndTrackingNumberIsNotNull(
                List.of(OrderStatus.OPEN, OrderStatus.SHIPPED));
        int delivered = 0;
        for (Order order : open) {
            if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
                continue;
            }
            ShippingCarrier carrier = ShippingCarrier.resolve(order.getShippingProvider(), order.getTrackingNumber());
            if (carrier == null || !carrier.trackable()) {
                continue;
            }
            try {
                CarrierTrackingClient.Snapshot snapshot = tracking.track(carrier, order.getTrackingNumber());
                if (!snapshot.delivered()) {
                    continue;
                }
                orderService.markDeliveredFromCarrier(order.getId());
                delivered += 1;
                log.info(
                        "Marked sales order {} delivered from {} tracking {}",
                        order.getExternalOrderId(),
                        carrier,
                        order.getTrackingNumber()
                );
            } catch (RuntimeException e) {
                log.warn(
                        "Could not update sales order {} from tracking {}: {}",
                        order.getExternalOrderId(),
                        order.getTrackingNumber(),
                        e.getMessage()
                );
            }
        }
        return delivered;
    }
}
