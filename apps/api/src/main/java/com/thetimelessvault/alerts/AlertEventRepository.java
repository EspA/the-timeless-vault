package com.thetimelessvault.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    Optional<AlertEvent> findByDedupeKey(String dedupeKey);

    List<AlertEvent> findAllByOrderByCreatedAtDesc();

    List<AlertEvent> findByReadAtIsNull();

    long countByReadAtIsNull();
}
