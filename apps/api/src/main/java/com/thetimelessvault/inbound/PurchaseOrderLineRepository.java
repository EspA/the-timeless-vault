package com.thetimelessvault.inbound;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    boolean existsByInventoryItemIdAndPurchaseOrder_Status(UUID inventoryItemId, PurchaseOrderStatus status);

    boolean existsByInventoryItemIdAndPurchaseOrder_StatusIn(UUID inventoryItemId, Collection<PurchaseOrderStatus> statuses);

    Optional<PurchaseOrderLine> findByInventoryItemId(UUID inventoryItemId);

    List<PurchaseOrderLine> findByPurchaseOrderId(UUID purchaseOrderId);
}
