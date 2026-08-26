package com.thetimelessvault.inbound;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    @Id
    private UUID id;

    @Column(name = "po_number", nullable = false, unique = true)
    private int poNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status = PurchaseOrderStatus.IN_TRANSIT;

    @Column(name = "expected_arrival")
    private LocalDate expectedArrival;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    private ShippingCarrier carrier;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PurchaseOrder create(Supplier supplier, int poNumber) {
        PurchaseOrder order = new PurchaseOrder();
        order.id = UUID.randomUUID();
        order.poNumber = poNumber;
        order.supplier = supplier;
        order.status = PurchaseOrderStatus.IN_TRANSIT;
        order.createdAt = Instant.now();
        order.updatedAt = Instant.now();
        return order;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void addLine(PurchaseOrderLine line) {
        line.setPurchaseOrder(this);
        lines.add(line);
    }

    public BigDecimal totalValue() {
        return lines.stream()
                .map(PurchaseOrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String displayNumber() {
        return "PO-" + poNumber;
    }

    public boolean isOpen() {
        return status != null && status.isOpen();
    }

    public UUID getId() {
        return id;
    }

    public int getPoNumber() {
        return poNumber;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus status) {
        this.status = status;
    }

    public LocalDate getExpectedArrival() {
        return expectedArrival;
    }

    public void setExpectedArrival(LocalDate expectedArrival) {
        this.expectedArrival = expectedArrival;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public ShippingCarrier getCarrier() {
        return carrier;
    }

    public void setCarrier(ShippingCarrier carrier) {
        this.carrier = carrier;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
