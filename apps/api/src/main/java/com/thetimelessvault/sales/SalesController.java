package com.thetimelessvault.sales;

import com.thetimelessvault.common.Platform;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesService sales;
    private final SalesSyncService sync;

    public SalesController(SalesService sales, SalesSyncService sync) {
        this.sales = sales;
        this.sync = sync;
    }

    public record SaleView(
            UUID id,
            UUID inventoryItemId,
            String sku,
            String setNumber,
            String itemTitle,
            Platform platform,
            String externalOrderId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal shippingCost,
            BigDecimal platformFee,
            String currency,
            Instant soldAt,
            String orderUrl,
            boolean inventoryCreated
    ) {
        static SaleView from(Sale sale) {
            return new SaleView(
                    sale.getId(),
                    sale.getInventoryItemId(),
                    sale.getSku(),
                    sale.getSetNumber(),
                    sale.getItemTitle(),
                    sale.getPlatform(),
                    sale.getExternalOrderId(),
                    sale.getQuantity(),
                    sale.getUnitPrice(),
                    sale.getShippingCost(),
                    sale.getPlatformFee(),
                    sale.getCurrency(),
                    sale.getSoldAt(),
                    sale.getOrderUrl(),
                    sale.isInventoryCreated()
            );
        }
    }

    public record SalesPageView(
            List<SaleView> items,
            int page,
            int size,
            long total,
            int totalPages,
            Instant lastSyncedAt
    ) {
    }

    @GetMapping
    public SalesPageView list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = sales.list(page, size);
        return new SalesPageView(
                result.getContent().stream().map(SaleView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                sales.lastSyncedAt().orElse(null)
        );
    }

    @PostMapping
    public SaleView create(@RequestBody SalesService.ManualSaleRequest request) {
        return SaleView.from(sales.addManual(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sales.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public Map<String, Object> syncNow() {
        return sync.sync();
    }
}
