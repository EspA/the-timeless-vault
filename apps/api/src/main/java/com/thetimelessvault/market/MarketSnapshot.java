package com.thetimelessvault.market;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_snapshot")
public class MarketSnapshot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id")
    private CatalogItem catalogItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private String condition;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(name = "avg_price")
    private BigDecimal avgPrice;

    @Column(name = "median_price")
    private BigDecimal medianPrice;

    @Column(name = "listing_count")
    private Integer listingCount;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static MarketSnapshot create(CatalogItem catalog, Platform platform, String condition) {
        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.catalogItem = catalog;
        snapshot.platform = platform;
        snapshot.condition = condition;
        snapshot.scannedAt = Instant.now();
        snapshot.createdAt = Instant.now();
        return snapshot;
    }

    public UUID getId() {
        return id;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getCondition() {
        return condition;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public BigDecimal getMedianPrice() {
        return medianPrice;
    }

    public void setMedianPrice(BigDecimal medianPrice) {
        this.medianPrice = medianPrice;
    }

    public Integer getListingCount() {
        return listingCount;
    }

    public void setListingCount(Integer listingCount) {
        this.listingCount = listingCount;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }
}
