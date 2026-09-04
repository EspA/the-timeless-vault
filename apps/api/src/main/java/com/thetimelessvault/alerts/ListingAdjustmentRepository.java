package com.thetimelessvault.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingAdjustmentRepository extends JpaRepository<ListingAdjustment, UUID> {
    Optional<ListingAdjustment> findByChannelListingId(UUID channelListingId);

    @Query("""
            select a from ListingAdjustment a
            join fetch a.channelListing l
            join fetch l.inventoryItem i
            join fetch i.catalogItem
            where a.id = :id
            """)
    Optional<ListingAdjustment> findByIdWithListing(@Param("id") UUID id);

    @Query("""
            select a from ListingAdjustment a
            join fetch a.channelListing l
            join fetch l.inventoryItem i
            join fetch i.catalogItem
            where a.status = :status
            order by a.detectedAt desc
            """)
    List<ListingAdjustment> findByStatusWithListing(@Param("status") ListingAdjustmentStatus status);

    default List<ListingAdjustment> findActiveWithListing() {
        return findByStatusWithListing(ListingAdjustmentStatus.ACTIVE);
    }
}
