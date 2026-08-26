package com.thetimelessvault.inbound;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService suppliers;

    public SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    public List<PurchaseOrderDtos.SupplierView> list() {
        return suppliers.list().stream().map(PurchaseOrderDtos.SupplierView::from).toList();
    }

    @GetMapping("/{id}")
    public PurchaseOrderDtos.SupplierView get(@PathVariable UUID id) {
        return PurchaseOrderDtos.SupplierView.from(suppliers.get(id));
    }

    @PostMapping
    public PurchaseOrderDtos.SupplierView create(@RequestBody SupplierService.SupplierRequest request) {
        return PurchaseOrderDtos.SupplierView.from(suppliers.create(request));
    }

    @PutMapping("/{id}")
    public PurchaseOrderDtos.SupplierView update(@PathVariable UUID id, @RequestBody SupplierService.SupplierRequest request) {
        return PurchaseOrderDtos.SupplierView.from(suppliers.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        suppliers.delete(id);
        return ResponseEntity.noContent().build();
    }
}
