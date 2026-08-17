package com.thetimelessvault.market;

import com.thetimelessvault.catalog.CatalogItem;
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
@Table(name = "market_scan_log")
public class MarketScanLog {

    @Id
    private UUID id;

    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(name = "set_number", nullable = false)
    private String setNumber;

    @Column(name = "set_name")
    private String setName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_trigger")
    private ScanTrigger scanTrigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    @Column(name = "listing_count")
    private Integer listingCount;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    public static MarketScanLog create(
            CatalogItem catalog,
            Platform platform,
            ScanTrigger trigger,
            ScanStatus status,
            Integer listingCount,
            String message
    ) {
        MarketScanLog log = new MarketScanLog();
        log.id = UUID.randomUUID();
        log.catalogItemId = catalog.getId();
        log.setNumber = catalog.getSetNumber();
        log.setName = catalog.getName();
        log.platform = platform;
        log.scanTrigger = trigger;
        log.status = status;
        log.listingCount = listingCount;
        log.message = message;
        log.scannedAt = Instant.now();
        return log;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatalogItemId() {
        return catalogItemId;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public String getSetName() {
        return setName;
    }

    public Platform getPlatform() {
        return platform;
    }

    public ScanTrigger getScanTrigger() {
        return scanTrigger;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public Integer getListingCount() {
        return listingCount;
    }

    public String getMessage() {
        return message;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }
}
