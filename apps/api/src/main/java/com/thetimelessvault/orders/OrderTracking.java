package com.thetimelessvault.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_tracking")
public class OrderTracking {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "tracking_number", nullable = false)
    private String trackingNumber;

    private String carrier;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static OrderTracking create(Order order, String trackingNumber, String carrier, int sortOrder) {
        OrderTracking tracking = new OrderTracking();
        tracking.id = UUID.randomUUID();
        tracking.order = order;
        tracking.trackingNumber = trackingNumber;
        tracking.carrier = carrier;
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

    public Order getOrder() {
        return order;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
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
