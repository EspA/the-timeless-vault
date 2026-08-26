package com.thetimelessvault.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_line")
public class PurchaseOrderLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(name = "set_number", nullable = false)
    private String setNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "unit_value", nullable = false)
    private BigDecimal unitValue;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PurchaseOrderLine create(String setNumber, String title, int quantity, BigDecimal unitValue) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.id = UUID.randomUUID();
        line.setNumber = setNumber;
        line.title = title;
        line.quantity = quantity;
        line.unitValue = unitValue;
        line.createdAt = Instant.now();
        line.updatedAt = Instant.now();
        return line;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public BigDecimal lineTotal() {
        return unitValue.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public void setPurchaseOrder(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(String setNumber) {
        this.setNumber = setNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitValue() {
        return unitValue;
    }

    public void setUnitValue(BigDecimal unitValue) {
        this.unitValue = unitValue;
    }

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public void setInventoryItemId(UUID inventoryItemId) {
        this.inventoryItemId = inventoryItemId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
