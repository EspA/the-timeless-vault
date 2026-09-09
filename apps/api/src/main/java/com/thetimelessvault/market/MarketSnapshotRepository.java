package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, UUID> {
    Optional<MarketSnapshot> findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(
            UUID catalogItemId, Platform platform, String condition);

    List<MarketSnapshot> findByCatalogItemIdOrderByScannedAtDesc(UUID catalogItemId);

    @Query(value = """
            SELECT s.*
            FROM catalog_item c
            CROSS JOIN LATERAL (
                SELECT ms.*
                FROM market_snapshot ms
                WHERE ms.catalog_item_id = c.id AND ms.platform = 'EBAY'
                ORDER BY ms.scanned_at DESC
                LIMIT 1
            ) s
            WHERE c.id IN (:catalogIds)
            UNION ALL
            SELECT s.*
            FROM catalog_item c
            CROSS JOIN LATERAL (
                SELECT ms.*
                FROM market_snapshot ms
                WHERE ms.catalog_item_id = c.id AND ms.platform = 'BRICKLINK'
                ORDER BY ms.scanned_at DESC
                LIMIT 1
            ) s
            WHERE c.id IN (:catalogIds)
            """, nativeQuery = true)
    List<MarketSnapshot> findLatestByCatalogItemIdIn(@Param("catalogIds") Collection<UUID> catalogIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            DELETE FROM market_snapshot
            WHERE id IN (
                SELECT id FROM (
                    SELECT s.id
                    FROM market_snapshot s
                    WHERE s.scanned_at < :cutoff
                      AND s.id NOT IN (
                          SELECT latest.id FROM (
                              SELECT DISTINCT ON (catalog_item_id, platform, condition) id
                              FROM market_snapshot
                              ORDER BY catalog_item_id, platform, condition, scanned_at DESC
                          ) latest
                      )
                    LIMIT 500
                ) stale
            )
            """, nativeQuery = true)
    int deleteStaleBefore(@Param("cutoff") Instant cutoff);
}
