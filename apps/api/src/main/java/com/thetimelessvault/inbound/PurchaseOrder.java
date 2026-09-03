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
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, createdAt ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<PurchaseOrderTracking> trackings = new ArrayList<>();

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

    public record TrackingDraft(String trackingNumber, ShippingCarrier carrier) {
    }

    public void replaceTrackings(List<TrackingDraft> shipments) {
        Map<String, TrackingDraft> unique = new LinkedHashMap<>();
        if (shipments != null) {
            for (TrackingDraft shipment : shipments) {
                if (shipment == null || shipment.trackingNumber() == null || shipment.trackingNumber().isBlank()) {
                    continue;
                }
                unique.putIfAbsent(shipment.trackingNumber().trim().toUpperCase(Locale.ROOT), shipment);
            }
        }
        Map<String, PurchaseOrderTracking> existingByKey = new LinkedHashMap<>();
        for (PurchaseOrderTracking existing : trackings) {
            if (existing.getTrackingNumber() != null) {
                existingByKey.putIfAbsent(existing.getTrackingNumber().trim().toUpperCase(Locale.ROOT), existing);
            }
        }
        trackings.removeIf(existing -> existing.getTrackingNumber() == null
                || !unique.containsKey(existing.getTrackingNumber().trim().toUpperCase(Locale.ROOT)));
        int index = 0;
        for (TrackingDraft shipment : unique.values()) {
            String number = shipment.trackingNumber().trim();
            String key = number.toUpperCase(Locale.ROOT);
            PurchaseOrderTracking row = existingByKey.get(key);
            if (row == null) {
                trackings.add(PurchaseOrderTracking.create(this, number, shipment.carrier(), index));
            } else {
                row.setCarrier(ShippingCarrier.resolve(
                        shipment.carrier() == null ? null : shipment.carrier().name(),
                        number
                ));
                row.setSortOrder(index);
            }
            index++;
        }
        syncPrimaryTracking();
    }

    public void replaceTracking(String number, ShippingCarrier nextCarrier) {
        if (number == null || number.isBlank()) {
            replaceTrackings(List.of());
            return;
        }
        replaceTrackings(List.of(new TrackingDraft(number, nextCarrier)));
    }

    private void syncPrimaryTracking() {
        if (trackings.isEmpty()) {
            this.trackingNumber = null;
            this.carrier = null;
            return;
        }
        PurchaseOrderTracking first = trackings.getFirst();
        this.trackingNumber = first.getTrackingNumber();
        this.carrier = first.getCarrier();
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
        replaceTracking(trackingNumber, carrier);
    }

    public ShippingCarrier getCarrier() {
        return carrier;
    }

    public void setCarrier(ShippingCarrier carrier) {
        if (trackings.isEmpty()) {
            this.carrier = carrier;
            if (trackingNumber != null && !trackingNumber.isBlank()) {
                replaceTracking(trackingNumber, carrier);
            }
            return;
        }
        trackings.getFirst().setCarrier(carrier);
        syncPrimaryTracking();
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

    public List<PurchaseOrderTracking> getTrackings() {
        return trackings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
