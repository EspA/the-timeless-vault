package com.thetimelessvault.publish;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ListingLogRepository extends JpaRepository<ListingLog, UUID> {
    Page<ListingLog> findAllByOrderByLoggedAtDesc(Pageable pageable);
}
