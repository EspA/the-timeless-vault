package com.thetimelessvault.inbound;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    public PurchaseOrderDtos.PurchaseOrderView syncView(UUID id) {
        PurchaseOrder order = purchaseOrders.get(id);
        if (!order.isOpen()) {
            throw ApiException.badRequest("Only In transit or Delivered purchase orders can be synced");
        }
        String trackingNumber = order.getTrackingNumber();
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw ApiException.badRequest("Add a tracking number before syncing");
        }
        ShippingCarrier carrier = order.getCarrier();
        if (carrier == null) {
            carrier = ShippingCarrier.fromTrackingNumber(trackingNumber);
        }
        if (carrier == null || !carrier.trackable()) {
            throw ApiException.badRequest("Choose UPS, USPS, or FedEx to sync tracking");
        }
        try {
            applySnapshot(order, tracking.trackRequired(carrier, trackingNumber));
        } catch (IllegalStateException e) {
            throw ApiException.unavailable(e.getMessage());
        }
        return purchaseOrders.view(id);
    }

    public int syncDeliveredShipments() {
        List<PurchaseOrder> inbound = orders.findByStatusInAndTrackingNumberIsNotNull(
                List.of(PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELIVERED)
        );
        int delivered = 0;
        for (PurchaseOrder order : inbound) {
            if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
                continue;
            }
            ShippingCarrier carrier = order.getCarrier();
            if (carrier == null) {
                carrier = ShippingCarrier.fromTrackingNumber(order.getTrackingNumber());
            }
            if (carrier == null || !carrier.trackable()) {
                continue;
            }
            try {
                CarrierTrackingClient.Snapshot snapshot = tracking.track(carrier, order.getTrackingNumber());
                applySnapshot(order, snapshot);
                if (snapshot.delivered()) {
                    delivered += 1;
                    log.info("Marked {} delivered from {} tracking {}", order.displayNumber(), carrier, order.getTrackingNumber());
                }
            } catch (RuntimeException e) {
                log.warn("Could not update {} from tracking {}: {}", order.displayNumber(), order.getTrackingNumber(), e.getMessage());
            }
        }
        return delivered;
    }

    private void applySnapshot(PurchaseOrder order, CarrierTrackingClient.Snapshot snapshot) {
        if (snapshot.expectedArrival() != null) {
            purchaseOrders.applyExpectedArrival(order.getId(), snapshot.expectedArrival());
        }
        if (snapshot.delivered()) {
            purchaseOrders.markDelivered(order.getId());
        }
    }
}
