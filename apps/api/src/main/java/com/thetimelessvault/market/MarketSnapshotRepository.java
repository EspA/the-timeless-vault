package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, UUID> {
    Optional<MarketSnapshot> findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(
            UUID catalogItemId, Platform platform, String condition);

    List<MarketSnapshot> findByCatalogItemIdOrderByScannedAtDesc(UUID catalogItemId);

    @Query("""
            select s from MarketSnapshot s
            join fetch s.catalogItem c
            where c.id in :catalogIds
              and s.scannedAt = (
                  select max(s2.scannedAt) from MarketSnapshot s2
                  where s2.catalogItem.id = c.id and s2.platform = s.platform
              )
            """)
    List<MarketSnapshot> findLatestByCatalogItemIdIn(@Param("catalogIds") Collection<UUID> catalogIds);
}
