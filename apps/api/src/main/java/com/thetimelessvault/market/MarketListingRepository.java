package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from MarketListing m where m.platform = :platform and lower(m.seller) = lower(:seller)")
    int deleteByPlatformAndSellerIgnoreCase(@Param("platform") Platform platform, @Param("seller") String seller);
}
