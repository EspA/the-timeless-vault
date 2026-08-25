package com.thetimelessvault.market;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {
    Page<ScanLog> findAllByOrderByScannedAtDesc(Pageable pageable);

    long deleteByScannedAtBefore(Instant scannedAt);

    long deleteByCatalogItemId(UUID catalogItemId);

    long deleteBySetNumberIgnoreCase(String setNumber);
}
