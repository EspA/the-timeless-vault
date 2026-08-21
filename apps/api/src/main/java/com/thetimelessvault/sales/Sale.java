package com.thetimelessvault.sales;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale")
public class Sale {

    @Id
    private UUID id;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

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

    @Column(name = "shipping_cost", nullable = false)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "platform_fee", nullable = false)
    private BigDecimal platformFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Column(name = "order_url")
    private String orderUrl;

    @Column(name = "inventory_created", nullable = false)
    private boolean inventoryCreated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Sale create(InventoryItem item, ChannelSale incoming, boolean inventoryCreated) {
        Sale sale = new Sale();
        sale.id = UUID.randomUUID();
        sale.inventoryItemId = item.getId();
        sale.platform = incoming.platform();
        sale.externalOrderId = incoming.orderId();
        sale.externalLineId = incoming.identityLineId();
        sale.sku = item.getSku();
        sale.setNumber = item.getCatalogItem() == null ? incoming.setNumber() : item.getCatalogItem().getSetNumber();
        sale.itemTitle = item.getTitle() == null ? incoming.title() : item.getTitle();
        sale.quantity = incoming.quantity();
        sale.unitPrice = incoming.unitPrice();
        sale.shippingCost = incoming.shippingCost();
        sale.platformFee = incoming.platformFee();
        sale.currency = incoming.currency();
        sale.soldAt = incoming.soldAt();
        sale.orderUrl = incoming.orderUrl();
        sale.inventoryCreated = inventoryCreated;
        sale.createdAt = Instant.now();
        return sale;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getExternalOrderId() {
        return externalOrderId;
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

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public BigDecimal getPlatformFee() {
        return platformFee;
    }

    public void applyChannelCosts(ChannelSale incoming) {
        if (incoming == null) {
            return;
        }
        this.shippingCost = incoming.shippingCost();
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getSoldAt() {
        return soldAt;
    }

    public String getOrderUrl() {
        return orderUrl;
    }

    public boolean isInventoryCreated() {
        return inventoryCreated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
