package com.thetimelessvault.publish;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublishJobRepository extends JpaRepository<PublishJob, UUID> {
    List<PublishJob> findByInventoryItemIdOrderByCreatedAtDesc(UUID inventoryItemId);
}
