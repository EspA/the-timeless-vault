package com.thetimelessvault.inbound;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService orders;
    private final PurchaseOrderDeliverySyncService deliverySync;

    public PurchaseOrderController(PurchaseOrderService orders, PurchaseOrderDeliverySyncService deliverySync) {
        this.orders = orders;
        this.deliverySync = deliverySync;
    }

    @GetMapping
    public PurchaseOrderDtos.PurchaseOrderPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String total,
            @RequestParam(required = false) String qty,
            @RequestParam(required = false) String arrival,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String tracking
    ) {
        return orders.page(page, size, new PurchaseOrderSpecifications.Query(
                number, supplier, status, total, qty, arrival, carrier, tracking
        ));
    }

    @GetMapping("/{id}")
    public PurchaseOrderDtos.PurchaseOrderView get(@PathVariable UUID id) {
        return orders.view(id);
    }

    @PostMapping
    public PurchaseOrderDtos.PurchaseOrderView create(@RequestBody PurchaseOrderService.UpsertRequest request) {
        return orders.createView(request);
    }

    @PutMapping("/{id}")
    public PurchaseOrderDtos.PurchaseOrderView update(
            @PathVariable UUID id,
            @RequestBody PurchaseOrderService.UpsertRequest request
    ) {
        return orders.updateView(id, request);
    }

    @PatchMapping("/{id}")
    public PurchaseOrderDtos.PurchaseOrderView updateHeader(
            @PathVariable UUID id,
            @RequestBody PurchaseOrderService.HeaderRequest request
    ) {
        return orders.updateHeaderView(id, request);
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        return Map.of("status", "ok", "delivered", deliverySync.syncDeliveredShipments());
    }

    @PostMapping("/{id}/deliver")
    public PurchaseOrderDtos.PurchaseOrderView deliver(@PathVariable UUID id) {
        return orders.markDeliveredView(id);
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrderDtos.PurchaseOrderView receive(@PathVariable UUID id) {
        return orders.receiveView(id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderDtos.PurchaseOrderView cancel(@PathVariable UUID id) {
        return orders.cancelView(id);
    }

    @PostMapping("/{id}/sync")
    public PurchaseOrderDtos.PurchaseOrderView sync(@PathVariable UUID id) {
        return deliverySync.syncView(id);
    }
}
