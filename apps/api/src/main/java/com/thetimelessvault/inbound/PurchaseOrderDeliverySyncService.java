package com.thetimelessvault.inbound;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.shipping.CarrierTrackingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        List<PurchaseOrderTracking> packages = order.getTrackings();
        if (packages.isEmpty()) {
            throw ApiException.badRequest("Add a tracking number before syncing");
        }
        LocalDate latestEta = null;
        boolean anyTrackable = false;
        boolean allTrackableDelivered = true;
        for (PurchaseOrderTracking shipment : packages) {
            ShippingCarrier carrier = resolveCarrier(shipment);
            if (carrier == null || !carrier.trackable()) {
                continue;
            }
            anyTrackable = true;
            try {
                CarrierTrackingClient.Snapshot snapshot = tracking.trackRequired(carrier, shipment.getTrackingNumber());
                if (snapshot.expectedArrival() != null
                        && (latestEta == null || snapshot.expectedArrival().isAfter(latestEta))) {
                    latestEta = snapshot.expectedArrival();
                }
                if (snapshot.delivered()) {
                    shipment.markDelivered();
                } else {
                    allTrackableDelivered = false;
                }
            } catch (IllegalStateException e) {
                throw ApiException.unavailable(e.getMessage());
            }
        }
        if (!anyTrackable) {
            throw ApiException.badRequest("Choose UPS, USPS, or FedEx to sync tracking");
        }
        orders.save(order);
        if (latestEta != null) {
            purchaseOrders.applyExpectedArrival(order.getId(), latestEta);
        }
        if (allTrackableDelivered) {
            purchaseOrders.markDelivered(order.getId());
        }
        return purchaseOrders.view(id);
    }

    public int syncDeliveredShipments() {
        List<PurchaseOrder> inbound = orders.findByStatusInAndTrackingNumberIsNotNull(
                List.of(PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELIVERED)
        );
        int delivered = 0;
        for (PurchaseOrder order : inbound) {
            List<PurchaseOrderTracking> packages = order.getTrackings();
            if (packages.isEmpty()) {
                continue;
            }
            boolean anyTrackable = false;
            boolean allTrackableDelivered = true;
            LocalDate latestEta = null;
            for (PurchaseOrderTracking shipment : packages) {
                ShippingCarrier carrier = resolveCarrier(shipment);
                if (carrier == null || !carrier.trackable()) {
                    continue;
                }
                anyTrackable = true;
                if (shipment.getDeliveredAt() != null) {
                    continue;
                }
                try {
                    CarrierTrackingClient.Snapshot snapshot = tracking.track(carrier, shipment.getTrackingNumber());
                    if (snapshot.expectedArrival() != null
                            && (latestEta == null || snapshot.expectedArrival().isAfter(latestEta))) {
                        latestEta = snapshot.expectedArrival();
                    }
                    if (snapshot.delivered()) {
                        shipment.markDelivered();
                    } else {
                        allTrackableDelivered = false;
                    }
                } catch (RuntimeException e) {
                    allTrackableDelivered = false;
                    log.warn(
                            "Could not update {} from tracking {}: {}",
                            order.displayNumber(),
                            shipment.getTrackingNumber(),
                            e.getMessage()
                    );
                }
            }
            orders.save(order);
            if (latestEta != null) {
                purchaseOrders.applyExpectedArrival(order.getId(), latestEta);
            }
            if (!anyTrackable || !allTrackableDelivered) {
                continue;
            }
            purchaseOrders.markDelivered(order.getId());
            delivered += 1;
            log.info("Marked {} delivered from {} package(s)", order.displayNumber(), packages.size());
        }
        return delivered;
    }

    private static ShippingCarrier resolveCarrier(PurchaseOrderTracking shipment) {
        if (shipment.getCarrier() != null) {
            return shipment.getCarrier();
        }
        return ShippingCarrier.fromTrackingNumber(shipment.getTrackingNumber());
    }
}
