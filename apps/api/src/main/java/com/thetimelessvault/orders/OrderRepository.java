package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );

    Optional<Order> findByPlatformAndExternalOrderIdAndExternalLineId(
            Platform platform,
            String externalOrderId,
            String externalLineId
    );

    List<Order> findByPlatformAndStatusSource(Platform platform, OrderStatusSource statusSource);

    List<Order> findByPlatformAndStatusIn(Platform platform, Collection<OrderStatus> statuses);

    List<Order> findByStatusInAndTrackingNumberIsNotNull(Collection<OrderStatus> statuses);
}
