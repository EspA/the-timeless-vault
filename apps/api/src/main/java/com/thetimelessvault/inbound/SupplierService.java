package com.thetimelessvault.inbound;

import com.thetimelessvault.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchaseOrders;

    public SupplierService(SupplierRepository suppliers, PurchaseOrderRepository purchaseOrders) {
        this.suppliers = suppliers;
        this.purchaseOrders = purchaseOrders;
    }

    @Transactional(readOnly = true)
    public List<Supplier> list() {
        return suppliers.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Supplier get(UUID id) {
        return suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier not found"));
    }

    @Transactional
    public Supplier create(SupplierRequest request) {
        Supplier supplier = Supplier.create(requireName(request.name()));
        apply(supplier, request);
        return suppliers.save(supplier);
    }

    @Transactional
    public Supplier update(UUID id, SupplierRequest request) {
        Supplier supplier = get(id);
        supplier.setName(requireName(request.name()));
        apply(supplier, request);
        supplier.touch();
        return suppliers.save(supplier);
    }

    @Transactional
    public void delete(UUID id) {
        Supplier supplier = get(id);
        if (purchaseOrders.existsBySupplierId(id)) {
            throw ApiException.conflict("This supplier has purchase orders and cannot be deleted.");
        }
        suppliers.delete(supplier);
    }

    private static void apply(Supplier supplier, SupplierRequest request) {
        supplier.setEmail(blankToNull(request.email()));
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setWebsite(blankToNull(request.website()));
        supplier.setStreet(blankToNull(request.street()));
        supplier.setCity(blankToNull(request.city()));
        supplier.setZip(blankToNull(request.zip()));
        supplier.setCountry(normalizeCountry(request.country()));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Supplier name is required");
        }
        return name.trim();
    }

    private static String normalizeCountry(String country) {
        String value = blankToNull(country);
        if (value == null) {
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 2) {
            throw ApiException.badRequest("Country must be a 2-letter code");
        }
        return code;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record SupplierRequest(
            String name,
            String email,
            String phone,
            String website,
            String street,
            String city,
            String zip,
            String country
    ) {
    }
}
