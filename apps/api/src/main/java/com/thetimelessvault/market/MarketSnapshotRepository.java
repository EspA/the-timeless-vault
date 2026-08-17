package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, UUID> {
    Optional<MarketSnapshot> findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(
            UUID catalogItemId, Platform platform, String condition);

    List<MarketSnapshot> findByCatalogItemIdOrderByScannedAtDesc(UUID catalogItemId);
}
