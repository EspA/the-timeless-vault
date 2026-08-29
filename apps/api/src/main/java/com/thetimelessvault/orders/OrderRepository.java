package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @EntityGraph(attributePaths = "lines")
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<Order> findById(UUID id);

    boolean existsByPlatformAndExternalOrderId(Platform platform, String externalOrderId);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findByPlatformAndExternalOrderId(Platform platform, String externalOrderId);

    List<Order> findByPlatformAndStatusSource(Platform platform, OrderStatusSource statusSource);

    List<Order> findByPlatformAndStatusIn(Platform platform, Collection<OrderStatus> statuses);

    List<Order> findByStatusInAndTrackingNumberIsNotNull(Collection<OrderStatus> statuses);
}
