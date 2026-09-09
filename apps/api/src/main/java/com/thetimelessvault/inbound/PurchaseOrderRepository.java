package com.thetimelessvault.inbound;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

    boolean existsBySupplierId(UUID supplierId);

    List<PurchaseOrder> findByStatusInAndTrackingNumberIsNotNull(Collection<PurchaseOrderStatus> statuses);

    @Query("select po from PurchaseOrder po join fetch po.supplier left join fetch po.lines where po.id = :id")
    Optional<PurchaseOrder> findWithDetailsById(@Param("id") UUID id);

    @Query(value = "select nextval('purchase_order_number_seq')", nativeQuery = true)
    long nextPoNumber();
}
