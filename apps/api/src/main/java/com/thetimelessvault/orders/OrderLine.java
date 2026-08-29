package com.thetimelessvault.orders;

import com.thetimelessvault.inventory.InventoryItem;
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
@Table(name = "order_line")
public class OrderLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "external_line_id", nullable = false)
    private String externalLineId;

    private String sku;

    @Column(name = "set_number")
    private String setNumber;

    @Column(name = "item_title")
    private String itemTitle;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "inventory_created", nullable = false)
    private boolean inventoryCreated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static OrderLine create(InventoryItem item, ChannelOrder incoming, boolean inventoryCreated) {
        OrderLine line = new OrderLine();
        line.id = UUID.randomUUID();
        line.inventoryItemId = item == null ? null : item.getId();
        line.externalLineId = incoming.identityLineId();
        line.sku = item == null || item.getSku() == null ? incoming.sku() : item.getSku();
        line.setNumber = item == null || item.getCatalogItem() == null
                ? incoming.setNumber()
                : item.getCatalogItem().getSetNumber();
        line.itemTitle = item == null || item.getTitle() == null ? incoming.title() : item.getTitle();
        line.quantity = incoming.quantity();
        line.unitPrice = incoming.unitPrice();
        line.inventoryCreated = inventoryCreated;
        line.createdAt = Instant.now();
        return line;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
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

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public String getExternalLineId() {
        return externalLineId;
    }

    public String getSku() {
        return sku;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public boolean isInventoryCreated() {
        return inventoryCreated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
