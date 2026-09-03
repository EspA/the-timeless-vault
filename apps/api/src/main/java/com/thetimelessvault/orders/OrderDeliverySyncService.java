package com.thetimelessvault.orders;

import com.thetimelessvault.inbound.ShippingCarrier;
import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public int syncDeliveredShipments() {
        List<Order> open = orders.findByStatusInAndTrackingNumberIsNotNull(
                List.of(OrderStatus.OPEN, OrderStatus.SHIPPED));
        int delivered = 0;
        for (Order order : open) {
            List<OrderTracking> packages = order.getTrackings();
            if (packages.isEmpty()) {
                continue;
            }
            boolean anyTrackable = false;
            boolean allTrackableDelivered = true;
            for (OrderTracking shipment : packages) {
                ShippingCarrier carrier = ShippingCarrier.resolve(shipment.getCarrier(), shipment.getTrackingNumber());
                if (carrier == null || !carrier.trackable()) {
                    continue;
                }
                anyTrackable = true;
                if (shipment.getDeliveredAt() != null) {
                    continue;
                }
                try {
                    CarrierTrackingClient.Snapshot snapshot = tracking.track(carrier, shipment.getTrackingNumber());
                    if (snapshot.delivered()) {
                        shipment.markDelivered();
                    } else {
                        allTrackableDelivered = false;
                    }
                } catch (RuntimeException e) {
                    allTrackableDelivered = false;
                    log.warn(
                            "Could not update sales order {} from tracking {}: {}",
                            order.getExternalOrderId(),
                            shipment.getTrackingNumber(),
                            e.getMessage()
                    );
                }
            }
            orders.save(order);
            if (!anyTrackable || !allTrackableDelivered) {
                continue;
            }
            orderService.markDeliveredFromCarrier(order.getId());
            delivered += 1;
            log.info(
                    "Marked sales order {} delivered from {} package(s)",
                    order.getExternalOrderId(),
                    packages.size()
            );
        }
        return delivered;
    }
}
