package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;
    private final OrderSyncService sync;

    public OrderController(OrderService orders, OrderSyncService sync) {
        this.orders = orders;
        this.sync = sync;
    }

    public record OrderLineView(
            UUID id,
            UUID inventoryItemId,
            String sku,
            String setNumber,
            String itemTitle,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            boolean inventoryCreated
    ) {
        static OrderLineView from(OrderLine line) {
            return new OrderLineView(
                    line.getId(),
                    line.getInventoryItemId(),
                    line.getSku(),
                    line.getSetNumber(),
                    line.getItemTitle(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.lineTotal(),
                    line.isInventoryCreated()
            );
        }
    }

    public record OrderView(
            UUID id,
            UUID inventoryItemId,
            String sku,
            String setNumber,
            String itemTitle,
            Platform platform,
            String externalOrderId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal merchandiseTotal,
            BigDecimal shippingCost,
            BigDecimal platformFee,
            String currency,
            Instant soldAt,
            String orderUrl,
            boolean inventoryCreated,
            OrderStatus status,
            String trackingNumber,
            String shippingProvider,
            List<TrackingView> trackings,
            List<OrderLineView> lines
    ) {
        static OrderView from(Order order) {
            List<OrderLineView> lines = order.getLines().stream().map(OrderLineView::from).toList();
            List<TrackingView> trackings = order.getTrackings().stream().map(TrackingView::from).toList();
            OrderLine primary = order.primaryLine();
            return new OrderView(
                    order.getId(),
                    primary == null ? null : primary.getInventoryItemId(),
                    primary == null ? null : primary.getSku(),
                    primary == null ? null : primary.getSetNumber(),
                    primary == null ? null : primary.getItemTitle(),
                    order.getPlatform(),
                    order.getExternalOrderId(),
                    order.totalQuantity(),
                    primary == null ? BigDecimal.ZERO : primary.getUnitPrice(),
                    order.merchandiseTotal(),
                    order.getShippingCost(),
                    order.getPlatformFee(),
                    order.getCurrency(),
                    order.getSoldAt(),
                    order.getOrderUrl(),
                    order.isInventoryCreated(),
                    order.getStatus(),
                    order.getTrackingNumber(),
                    order.getShippingProvider(),
                    trackings,
                    lines
            );
        }
    }

    public record TrackingView(UUID id, String trackingNumber, String shippingProvider) {
        static TrackingView from(OrderTracking tracking) {
            return new TrackingView(tracking.getId(), tracking.getTrackingNumber(), tracking.getCarrier());
        }
    }

    public record OrdersPageView(
            List<OrderView> items,
            int page,
            int size,
            long total,
            int totalPages,
            Instant lastSyncedAt
    ) {
    }

    @GetMapping
    public OrdersPageView list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var result = orders.list(page, size);
        return new OrdersPageView(
                result.getContent().stream().map(OrderView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                orders.lastSyncedAt().orElse(null)
        );
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable UUID id) {
        return OrderView.from(orders.get(id));
    }

    @PostMapping
    public OrderView create(@RequestBody OrderService.ManualOrderRequest request) {
        return OrderView.from(orders.addManual(request));
    }

    @PutMapping("/{id}")
    public OrderView update(@PathVariable UUID id, @RequestBody OrderService.UpdateOrderRequest request) {
        return OrderView.from(orders.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        orders.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public Map<String, Object> syncNow() {
        return sync.sync();
    }
}
