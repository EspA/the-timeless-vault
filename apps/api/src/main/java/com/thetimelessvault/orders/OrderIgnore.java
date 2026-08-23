package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_ignore")
public class OrderIgnore {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "external_order_id", nullable = false)
    private String externalOrderId;

    @Column(name = "external_line_id", nullable = false)
    private String externalLineId;

    @Column(name = "ignored_at", nullable = false)
    private Instant ignoredAt;

    public static OrderIgnore of(Platform platform, String externalOrderId, String externalLineId) {
        OrderIgnore ignore = new OrderIgnore();
        ignore.id = UUID.randomUUID();
        ignore.platform = platform;
        ignore.externalOrderId = externalOrderId;
        ignore.externalLineId = externalLineId;
        ignore.ignoredAt = Instant.now();
        return ignore;
    }

    public UUID getId() {
        return id;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public String getExternalLineId() {
        return externalLineId;
    }

    public Instant getIgnoredAt() {
        return ignoredAt;
    }
}
