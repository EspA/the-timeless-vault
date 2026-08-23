package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderIgnoreRepository extends JpaRepository<OrderIgnore, UUID> {
    boolean existsByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );

    Optional<OrderIgnore> findByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );
}
