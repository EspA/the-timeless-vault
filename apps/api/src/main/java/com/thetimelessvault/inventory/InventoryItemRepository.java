package com.thetimelessvault.inventory;

import com.thetimelessvault.common.StockStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {

    @Query("select i from InventoryItem i join fetch i.catalogItem order by i.createdAt desc")
    List<InventoryItem> findAllWithCatalog();

    @Query("select i from InventoryItem i join fetch i.catalogItem where i.id = :id")
    Optional<InventoryItem> findWithCatalogById(UUID id);

    List<InventoryItem> findByCatalogItemId(UUID catalogItemId);

    @Query("select i from InventoryItem i join fetch i.catalogItem where lower(i.sku) = lower(:sku)")
    Optional<InventoryItem> findWithCatalogBySkuIgnoreCase(@Param("sku") String sku);

    @Query("select i from InventoryItem i join fetch i.catalogItem where i.stockStatus = :status order by i.createdAt asc")
    List<InventoryItem> findWithCatalogByStockStatus(@Param("status") StockStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InventoryItem i set i.ebayStoreCategory = null "
            + "where i.ebayStoreCategory = :name or i.ebayStoreCategory like concat(:name, '/%')")
    int clearEbayStoreCategory(@Param("name") String name);
}
