package com.thetimelessvault.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    @Query("select i from InventoryItem i join fetch i.catalogItem order by i.createdAt desc")
    List<InventoryItem> findAllWithCatalog();

    @Query("select i from InventoryItem i join fetch i.catalogItem where i.id = :id")
    Optional<InventoryItem> findWithCatalogById(UUID id);

    List<InventoryItem> findByCatalogItemId(UUID catalogItemId);
}
