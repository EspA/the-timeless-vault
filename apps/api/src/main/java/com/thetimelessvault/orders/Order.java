package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<OrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, createdAt ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<OrderTracking> trackings = new ArrayList<>();

    public static Order create(InventoryItem item, ChannelOrder incoming, boolean inventoryCreated) {
        Order order = header(incoming);
        order.addLine(OrderLine.create(item, incoming, inventoryCreated));
        return order;
    }

    static Order header(ChannelOrder incoming) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.platform = incoming.platform();
        order.externalOrderId = incoming.orderId();
        order.shippingCost = incoming.shippingCost();
        order.platformFee = incoming.platformFee();
        order.currency = incoming.currency();
        order.soldAt = incoming.soldAt();
        order.orderUrl = incoming.orderUrl();
        order.status = incoming.status() == null ? OrderStatus.OPEN : incoming.status();
        order.statusSource = OrderStatusSource.CHANNEL;
        order.mergeTracking(incoming.trackingNumber(), incoming.shippingProvider());
        Instant now = Instant.now();
        order.statusUpdatedAt = now;
        order.createdAt = now;
        return order;
    }

    public void addLine(OrderLine line) {
        line.setOrder(this);
        lines.add(line);
    }

    public void replaceTrackings(List<ShipmentTracking> shipments) {
        Map<String, ShipmentTracking> unique = new LinkedHashMap<>();
        if (shipments != null) {
            for (ShipmentTracking shipment : shipments) {
                if (shipment == null || shipment.trackingNumber() == null || shipment.trackingNumber().isBlank()) {
                    continue;
                }
                unique.putIfAbsent(shipment.trackingNumber().trim().toUpperCase(Locale.ROOT), shipment);
            }
        }
        Map<String, OrderTracking> existingByKey = new LinkedHashMap<>();
        for (OrderTracking existing : trackings) {
            if (existing.getTrackingNumber() != null) {
                existingByKey.putIfAbsent(existing.getTrackingNumber().trim().toUpperCase(Locale.ROOT), existing);
            }
        }
        trackings.removeIf(existing -> existing.getTrackingNumber() == null
                || !unique.containsKey(existing.getTrackingNumber().trim().toUpperCase(Locale.ROOT)));
        int index = 0;
        for (ShipmentTracking shipment : unique.values()) {
            ShipmentTracking normalized = ShipmentTracking.of(shipment.trackingNumber(), shipment.shippingProvider());
            String key = normalized.trackingNumber().toUpperCase(Locale.ROOT);
            OrderTracking row = existingByKey.get(key);
            if (row == null) {
                trackings.add(OrderTracking.create(this, normalized.trackingNumber(), normalized.shippingProvider(), index));
            } else {
                row.setCarrier(normalized.shippingProvider());
                row.setSortOrder(index);
            }
            index++;
        }
        syncPrimaryTracking();
    }

    public void mergeTracking(String number, String provider) {
        ShipmentTracking incoming = ShipmentTracking.of(number, provider);
        if (incoming == null) {
            syncPrimaryTracking();
            return;
        }
        String key = incoming.trackingNumber().toUpperCase(Locale.ROOT);
        for (OrderTracking existing : trackings) {
            if (existing.getTrackingNumber() != null && key.equals(existing.getTrackingNumber().toUpperCase(Locale.ROOT))) {
                if (incoming.shippingProvider() != null) {
                    existing.setCarrier(incoming.shippingProvider());
                }
                syncPrimaryTracking();
                return;
            }
        }
        trackings.add(OrderTracking.create(this, incoming.trackingNumber(), incoming.shippingProvider(), trackings.size()));
        syncPrimaryTracking();
    }

    private void syncPrimaryTracking() {
        if (trackings.isEmpty()) {
            this.trackingNumber = null;
            this.shippingProvider = ShippingProviders.DEFAULT;
            return;
        }
        OrderTracking first = trackings.getFirst();
        this.trackingNumber = first.getTrackingNumber();
        this.shippingProvider = ShippingProviders.defaulted(first.getCarrier());
    }

    public OrderLine findLine(String externalLineId) {
        String identity = externalLineId == null || externalLineId.isBlank() ? "0" : externalLineId;
        return lines.stream()
                .filter(line -> identity.equals(line.getExternalLineId()))
                .findFirst()
                .orElse(null);
    }

    public boolean hasLine(String externalLineId) {
        return findLine(externalLineId) != null;
    }

    public OrderLine primaryLine() {
        return lines.isEmpty() ? null : lines.getFirst();
    }

    public OrderLine lineFor(InventoryItem item) {
        if (item != null && item.getId() != null) {
            for (OrderLine line : lines) {
                if (item.getId().equals(line.getInventoryItemId())) {
                    return line;
                }
            }
        }
        return primaryLine();
    }

    public int totalQuantity() {
        return lines.stream().mapToInt(OrderLine::getQuantity).sum();
    }

    public BigDecimal merchandiseTotal() {
        return lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public OrderStatus applyChannelUpdate(ChannelOrder incoming) {
        OrderStatus previous = status;
        if (incoming == null) {
            return previous;
        }
        if (incoming.shippingCost() != null && incoming.shippingCost().signum() > 0) {
            this.shippingCost = incoming.shippingCost();
        }
        if (incoming.platformFee() != null && incoming.platformFee().signum() > 0) {
            this.platformFee = incoming.platformFee();
        }
        if (incoming.trackingNumber() != null) {
            if (incoming.platform() == Platform.BRICKLINK) {
                mergeTracking(incoming.trackingNumber(), incoming.shippingProvider());
            } else {
                mergeTracking(
                        incoming.trackingNumber(),
                        incoming.shippingProvider() != null ? incoming.shippingProvider() : shippingProvider
                );
            }
        } else {
            this.shippingProvider = ShippingProviders.defaulted(this.shippingProvider);
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
        return applyManual(nextStatus, tracking, provider, null);
    }

    public OrderStatus applyManual(
            OrderStatus nextStatus,
            String tracking,
            String provider,
            List<ShipmentTracking> shipments
    ) {
        OrderStatus previous = status;
        if (shipments != null) {
            replaceTrackings(shipments);
        } else if (tracking != null || provider != null) {
            String number = tracking != null ? tracking : trackingNumber;
            String ship = provider != null ? provider : shippingProvider;
            replaceTrackings(number == null || number.isBlank()
                    ? List.of()
                    : List.of(ShipmentTracking.of(number, ship)));
        }
        if (nextStatus != null && nextStatus != status) {
            this.status = nextStatus;
            this.statusSource = OrderStatusSource.MANUAL;
            this.statusUpdatedAt = Instant.now();
        }
        return previous;
    }

    public OrderStatus markDeliveredFromCarrier() {
        OrderStatus previous = status;
        if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
            return previous;
        }
        this.status = OrderStatus.COMPLETED;
        this.statusUpdatedAt = Instant.now();
        return previous;
    }

    void markMigrated(OrderStatus migratedStatus) {
        this.status = migratedStatus == null ? OrderStatus.COMPLETED : migratedStatus;
        this.statusSource = OrderStatusSource.MIGRATION;
    }

    public void setPlatformFee(BigDecimal platformFee) {
        this.platformFee = platformFee == null ? BigDecimal.ZERO : platformFee;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInventoryItemId() {
        OrderLine line = primaryLine();
        return line == null ? null : line.getInventoryItemId();
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public String getExternalLineId() {
        OrderLine line = primaryLine();
        return line == null ? null : line.getExternalLineId();
    }

    public String getSku() {
        OrderLine line = primaryLine();
        return line == null ? null : line.getSku();
    }

    public String getSetNumber() {
        OrderLine line = primaryLine();
        return line == null ? null : line.getSetNumber();
    }

    public String getItemTitle() {
        OrderLine line = primaryLine();
        return line == null ? null : line.getItemTitle();
    }

    public int getQuantity() {
        return totalQuantity();
    }

    public BigDecimal getUnitPrice() {
        OrderLine line = primaryLine();
        return line == null ? BigDecimal.ZERO : line.getUnitPrice();
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
        return lines.stream().anyMatch(OrderLine::isInventoryCreated);
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

    public List<OrderLine> getLines() {
        return lines;
    }

    public List<OrderTracking> getTrackings() {
        return trackings;
    }
}
