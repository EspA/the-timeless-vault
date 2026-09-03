package com.thetimelessvault.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_tracking")
public class PurchaseOrderTracking {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(name = "tracking_number", nullable = false)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    private ShippingCarrier carrier;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PurchaseOrderTracking create(
            PurchaseOrder purchaseOrder,
            String trackingNumber,
            ShippingCarrier carrier,
            int sortOrder
    ) {
        PurchaseOrderTracking tracking = new PurchaseOrderTracking();
        tracking.id = UUID.randomUUID();
        tracking.purchaseOrder = purchaseOrder;
        tracking.trackingNumber = trackingNumber;
        tracking.carrier = ShippingCarrier.resolve(
                carrier == null ? null : carrier.name(),
                trackingNumber
        );
        tracking.sortOrder = sortOrder;
        tracking.createdAt = Instant.now();
        return tracking;
    }

    public void markDelivered() {
        if (deliveredAt == null) {
            deliveredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    void setPurchaseOrder(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShippingCarrier getCarrier() {
        return carrier;
    }

    public void setCarrier(ShippingCarrier carrier) {
        this.carrier = carrier;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
