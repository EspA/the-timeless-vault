package com.thetimelessvault.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {
    Optional<CatalogItem> findBySetNumberIgnoreCase(String setNumber);
}
