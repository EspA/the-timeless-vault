package com.thetimelessvault.sales;

import com.thetimelessvault.common.Platform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
    Page<Sale> findAllByOrderBySoldAtDesc(Pageable pageable);

    boolean existsByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );

    java.util.Optional<Sale> findByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );
}
