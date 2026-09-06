package com.thetimelessvault.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceGuardRepository extends JpaRepository<PriceGuard, UUID> {
    Optional<PriceGuard> findByChannelListingId(UUID channelListingId);

    @Query("select g from PriceGuard g join fetch g.channelListing l join fetch l.inventoryItem i join fetch i.catalogItem where g.enabled = true")
    List<PriceGuard> findEnabledWithListing();

    @Query("select l.id from PriceGuard g join g.channelListing l where g.enabled = false")
    List<UUID> findDisabledListingIds();

    @Query("select g from PriceGuard g join fetch g.channelListing")
    List<PriceGuard> findAllWithListing();
}
