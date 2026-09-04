package com.thetimelessvault.alerts;

import com.thetimelessvault.publish.ChannelListing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "listing_adjustment")
public class ListingAdjustment {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/New_York");

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_listing_id")
    private ChannelListing channelListing;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingAdjustmentStatus status = ListingAdjustmentStatus.ACTIVE;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "your_price")
    private BigDecimal yourPrice;

    @Column(name = "market_price")
    private BigDecimal marketPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ListingAdjustment create(
            ChannelListing listing,
            String type,
            BigDecimal yourPrice,
            BigDecimal marketPrice,
            Instant detectedAt
    ) {
        ListingAdjustment row = new ListingAdjustment();
        row.id = UUID.randomUUID();
        row.channelListing = listing;
        row.type = type;
        row.status = ListingAdjustmentStatus.ACTIVE;
        row.detectedAt = detectedAt;
        row.yourPrice = yourPrice;
        row.marketPrice = marketPrice;
        row.createdAt = detectedAt;
        row.updatedAt = detectedAt;
        return row;
    }

    public void activate(String type, BigDecimal yourPrice, BigDecimal marketPrice, Instant detectedAt) {
        this.type = type;
        this.status = ListingAdjustmentStatus.ACTIVE;
        this.detectedAt = detectedAt;
        this.dismissedAt = null;
        this.yourPrice = yourPrice;
        this.marketPrice = marketPrice;
        this.updatedAt = detectedAt;
    }

    public void refresh(BigDecimal yourPrice, BigDecimal marketPrice, Instant now) {
        this.yourPrice = yourPrice;
        this.marketPrice = marketPrice;
        this.updatedAt = now;
    }

    public void dismiss(Instant now) {
        this.status = ListingAdjustmentStatus.DISMISSED;
        this.dismissedAt = now;
        this.updatedAt = now;
    }

    public void resolve(Instant now) {
        this.status = ListingAdjustmentStatus.RESOLVED;
        this.dismissedAt = null;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == ListingAdjustmentStatus.ACTIVE;
    }

    public boolean isDismissed() {
        return status == ListingAdjustmentStatus.DISMISSED;
    }

    public boolean isResolved() {
        return status == ListingAdjustmentStatus.RESOLVED;
    }

    public boolean dismissedOnPriorDay(Instant now) {
        if (dismissedAt == null) {
            return true;
        }
        LocalDate dismissedDay = dismissedAt.atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate today = now.atZone(BUSINESS_ZONE).toLocalDate();
        return dismissedDay.isBefore(today);
    }

    public UUID getId() {
        return id;
    }

    public ChannelListing getChannelListing() {
        return channelListing;
    }

    public String getType() {
        return type;
    }

    public ListingAdjustmentStatus getStatus() {
        return status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getDismissedAt() {
        return dismissedAt;
    }

    public BigDecimal getYourPrice() {
        return yourPrice;
    }

    public BigDecimal getMarketPrice() {
        return marketPrice;
    }
}
