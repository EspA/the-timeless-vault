package com.thetimelessvault.inbound;

import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PurchaseOrderDeliverySyncService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderDeliverySyncService.class);

    private final PurchaseOrderRepository orders;
    private final PurchaseOrderService purchaseOrders;
    private final CarrierTrackingClient tracking;

    public PurchaseOrderDeliverySyncService(
            PurchaseOrderRepository orders,
            PurchaseOrderService purchaseOrders,
            CarrierTrackingClient tracking
    ) {
        this.orders = orders;
        this.purchaseOrders = purchaseOrders;
        this.tracking = tracking;
    }

    public int syncDeliveredShipments() {
        List<PurchaseOrder> inbound = orders.findByStatusAndTrackingNumberIsNotNull(PurchaseOrderStatus.IN_TRANSIT);
        int delivered = 0;
        for (PurchaseOrder order : inbound) {
            if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
                continue;
            }
            ShippingCarrier carrier = order.getCarrier();
            if (carrier == null || !carrier.trackable()) {
                continue;
            }
            try {
                CarrierTrackingClient.Snapshot snapshot = tracking.track(carrier, order.getTrackingNumber());
                if (snapshot.expectedArrival() != null) {
                    purchaseOrders.applyExpectedArrival(order.getId(), snapshot.expectedArrival());
                }
                if (!snapshot.delivered()) {
                    continue;
                }
                purchaseOrders.markDelivered(order.getId());
                delivered += 1;
                log.info("Marked {} delivered from {} tracking {}", order.displayNumber(), carrier, order.getTrackingNumber());
            } catch (RuntimeException e) {
                log.warn("Could not update {} from tracking {}: {}", order.displayNumber(), order.getTrackingNumber(), e.getMessage());
            }
        }
        return delivered;
    }
}
