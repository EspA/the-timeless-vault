package com.thetimelessvault.alerts;

import com.thetimelessvault.publish.ChannelListing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_guard")
public class PriceGuard {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_listing_id")
    private ChannelListing channelListing;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "high_percent", nullable = false)
    private BigDecimal highPercent = new BigDecimal("15");

    @Column(name = "low_percent", nullable = false)
    private BigDecimal lowPercent = new BigDecimal("15");

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PriceGuard create(ChannelListing listing) {
        PriceGuard guard = new PriceGuard();
        guard.id = UUID.randomUUID();
        guard.channelListing = listing;
        guard.createdAt = Instant.now();
        guard.updatedAt = Instant.now();
        return guard;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ChannelListing getChannelListing() {
        return channelListing;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getHighPercent() {
        return highPercent;
    }

    public void setHighPercent(BigDecimal highPercent) {
        this.highPercent = highPercent;
    }

    public BigDecimal getLowPercent() {
        return lowPercent;
    }

    public void setLowPercent(BigDecimal lowPercent) {
        this.lowPercent = lowPercent;
    }
}
