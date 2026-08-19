package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketListingRepository extends JpaRepository<MarketListing, UUID> {
    List<MarketListing> findBySnapshotId(UUID snapshotId);

    List<MarketListing> findByCatalogItemIdAndPlatform(UUID catalogItemId, Platform platform);

    List<MarketListing> findByCatalogItemIdAndPlatformAndSnapshotScanTrigger(
            UUID catalogItemId,
            Platform platform,
            ScanTrigger scanTrigger
    );
}
