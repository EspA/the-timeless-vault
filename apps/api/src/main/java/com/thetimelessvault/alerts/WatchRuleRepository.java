package com.thetimelessvault.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchRuleRepository extends JpaRepository<WatchRule, UUID> {
    Optional<WatchRule> findByCatalogItemId(UUID catalogItemId);

    @Query("select r from WatchRule r join fetch r.catalogItem where r.enabled = true")
    List<WatchRule> findEnabledWithCatalog();

    @Query("select r from WatchRule r join fetch r.catalogItem")
    List<WatchRule> findAllWithCatalog();
}
