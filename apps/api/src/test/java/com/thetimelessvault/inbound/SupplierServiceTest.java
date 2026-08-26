package com.thetimelessvault.inbound;

import com.thetimelessvault.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock SupplierRepository suppliers;
    @Mock PurchaseOrderRepository purchaseOrders;

    SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(suppliers, purchaseOrders);
    }

    @Test
    void deleteIsBlockedWhenPurchaseOrdersExist() {
        Supplier supplier = Supplier.create("Brick Depot");
        when(suppliers.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(purchaseOrders.existsBySupplierId(supplier.getId())).thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () -> service.delete(supplier.getId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("This supplier has purchase orders and cannot be deleted.", error.getMessage());
        verify(suppliers, never()).delete(any());
    }
}
