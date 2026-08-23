package com.thetimelessvault.orders;

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
@Table(name = "orders")
public class Order {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_source", nullable = false)
    private OrderStatusSource statusSource = OrderStatusSource.CHANNEL;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "shipping_provider")
    private String shippingProvider;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Order create(InventoryItem item, ChannelOrder incoming, boolean inventoryCreated) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.inventoryItemId = item == null ? null : item.getId();
        order.platform = incoming.platform();
        order.externalOrderId = incoming.orderId();
        order.externalLineId = incoming.identityLineId();
        order.sku = item == null || item.getSku() == null ? incoming.sku() : item.getSku();
        order.setNumber = item == null || item.getCatalogItem() == null
                ? incoming.setNumber()
                : item.getCatalogItem().getSetNumber();
        order.itemTitle = item == null || item.getTitle() == null ? incoming.title() : item.getTitle();
        order.quantity = incoming.quantity();
        order.unitPrice = incoming.unitPrice();
        order.shippingCost = incoming.shippingCost();
        order.platformFee = incoming.platformFee();
        order.currency = incoming.currency();
        order.soldAt = incoming.soldAt();
        order.orderUrl = incoming.orderUrl();
        order.inventoryCreated = inventoryCreated;
        order.status = incoming.status() == null ? OrderStatus.OPEN : incoming.status();
        order.statusSource = OrderStatusSource.CHANNEL;
        order.trackingNumber = incoming.trackingNumber();
        order.shippingProvider = ShippingProviders.infer(order.trackingNumber, incoming.shippingProvider());
        Instant now = Instant.now();
        order.statusUpdatedAt = now;
        order.createdAt = now;
        return order;
    }

    public OrderStatus applyChannelUpdate(ChannelOrder incoming) {
        OrderStatus previous = status;
        if (incoming == null) {
            return previous;
        }
        this.shippingCost = incoming.shippingCost();
        if (incoming.trackingNumber() != null) {
            this.trackingNumber = incoming.trackingNumber();
        }
        if (incoming.platform() == Platform.BRICKLINK) {
            this.shippingProvider = ShippingProviders.infer(this.trackingNumber, null);
        } else if (incoming.shippingProvider() != null) {
            this.shippingProvider = incoming.shippingProvider();
        } else {
            this.shippingProvider = ShippingProviders.infer(this.trackingNumber, this.shippingProvider);
        }
        if (shouldApplyIncomingStatus(incoming.status())) {
            this.status = incoming.status();
            this.statusUpdatedAt = Instant.now();
        }
        if (statusSource == OrderStatusSource.MIGRATION) {
            this.statusSource = OrderStatusSource.CHANNEL;
        }
        return previous;
    }

    private boolean shouldApplyIncomingStatus(OrderStatus incoming) {
        if (incoming == null) {
            return false;
        }
        if (statusSource == OrderStatusSource.MIGRATION) {
            return incoming != status;
        }
        if (statusSource == OrderStatusSource.MANUAL) {
            return incoming == OrderStatus.CANCELLED || status == OrderStatus.CANCELLED;
        }
        return OrderStatus.shouldApplyChannelStatus(status, incoming);
    }

    public OrderStatus applyManual(OrderStatus nextStatus, String tracking, String provider) {
        OrderStatus previous = status;
        if (tracking != null) {
            String trimmed = tracking.trim();
            this.trackingNumber = trimmed.isEmpty() ? null : trimmed;
        }
        if (provider != null) {
            String trimmed = provider.trim();
            this.shippingProvider = trimmed.isEmpty() ? null : trimmed;
        }
        this.shippingProvider = ShippingProviders.infer(this.trackingNumber, this.shippingProvider);
        if (nextStatus != null && nextStatus != status) {
            this.status = nextStatus;
            this.statusSource = OrderStatusSource.MANUAL;
            this.statusUpdatedAt = Instant.now();
        }
        return previous;
    }

    void markMigrated(OrderStatus migratedStatus) {
        this.status = migratedStatus == null ? OrderStatus.COMPLETED : migratedStatus;
        this.statusSource = OrderStatusSource.MIGRATION;
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

    public OrderStatus getStatus() {
        return status;
    }

    public OrderStatusSource getStatusSource() {
        return statusSource;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getShippingProvider() {
        return shippingProvider;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
