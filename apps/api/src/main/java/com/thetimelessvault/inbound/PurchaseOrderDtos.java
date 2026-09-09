package com.thetimelessvault.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    public record SupplierView(
            UUID id,
            String name,
            String email,
            String phone,
            String website,
            String street,
            String city,
            String zip,
            String country,
            Instant createdAt,
            Instant updatedAt
    ) {
        static SupplierView from(Supplier supplier) {
            return new SupplierView(
                    supplier.getId(),
                    supplier.getName(),
                    supplier.getEmail(),
                    supplier.getPhone(),
                    supplier.getWebsite(),
                    supplier.getStreet(),
                    supplier.getCity(),
                    supplier.getZip(),
                    supplier.getCountry(),
                    supplier.getCreatedAt(),
                    supplier.getUpdatedAt()
            );
        }
    }

    public record LineView(
            UUID id,
            String setNumber,
            String title,
            int quantity,
            BigDecimal unitValue,
            BigDecimal lineTotal,
            UUID inventoryItemId,
            String sku
    ) {
    }

    public record TrackingView(UUID id, String trackingNumber, ShippingCarrier carrier) {
        static TrackingView from(PurchaseOrderTracking tracking) {
            return new TrackingView(tracking.getId(), tracking.getTrackingNumber(), tracking.getCarrier());
        }
    }

    public record PurchaseOrderView(
            UUID id,
            String number,
            UUID supplierId,
            String supplierName,
            PurchaseOrderStatus status,
            BigDecimal totalValue,
            LocalDate expectedArrival,
            String trackingNumber,
            ShippingCarrier carrier,
            List<TrackingView> trackings,
            String note,
            List<LineView> lines,
            int lineCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PurchaseOrderPage(
            List<PurchaseOrderView> items,
            int page,
            int size,
            long total,
            int totalPages
    ) {
    }
}
