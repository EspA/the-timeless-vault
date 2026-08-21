package com.thetimelessvault.sales;

import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SaleIgnoreRepository extends JpaRepository<SaleIgnore, UUID> {
    boolean existsByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );

    Optional<SaleIgnore> findByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );
}
