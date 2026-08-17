package com.thetimelessvault.publish;

import com.thetimelessvault.common.ListingStatus;
import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelListingRepository extends JpaRepository<ChannelListing, UUID> {
    List<ChannelListing> findByInventoryItemId(UUID inventoryItemId);

    Optional<ChannelListing> findByInventoryItemIdAndPlatform(UUID inventoryItemId, Platform platform);

    @Query("select l from ChannelListing l join fetch l.inventoryItem i join fetch i.catalogItem where l.status = :status")
    List<ChannelListing> findAllByStatusWithItem(ListingStatus status);
}
