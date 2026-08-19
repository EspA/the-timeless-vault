package com.thetimelessvault.opportunities;

import com.thetimelessvault.market.ScanTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuyingOpportunityRepository extends JpaRepository<BuyingOpportunity, UUID> {
    Optional<BuyingOpportunity> findByDedupeKey(String dedupeKey);

    Optional<BuyingOpportunity> findByDedupeKeyAndScanTrigger(String dedupeKey, ScanTrigger scanTrigger);

    List<BuyingOpportunity> findAllByOrderByCreatedAtDesc();

    List<BuyingOpportunity> findByReadAtIsNull();

    long countByReadAtIsNull();

    long deleteByCatalogItem_IdAndType(UUID catalogItemId, String type);
}
