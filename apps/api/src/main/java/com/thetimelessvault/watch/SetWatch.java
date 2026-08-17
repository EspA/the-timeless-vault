package com.thetimelessvault.watch;

import com.thetimelessvault.catalog.CatalogItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "set_watch")
public class SetWatch {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id")
    private CatalogItem catalogItem;

    @Column(name = "set_number", nullable = false, unique = true)
    private String setNumber;

    @Column(nullable = false)
    private String name;

    private String theme;
    private String subtheme;

    @Column(name = "released_date")
    private LocalDate releasedDate;

    @Column(name = "retired_date")
    private LocalDate retiredDate;

    private Boolean retired;

    @Column(name = "pieces_count")
    private Integer piecesCount;

    @Column(name = "minifigs_count")
    private Integer minifigsCount;

    @Column(name = "retail_price_us")
    private BigDecimal retailPriceUs;

    @Column(name = "current_value_new")
    private BigDecimal currentValueNew;

    @Column(name = "brickeconomy_fetched_at")
    private Instant brickeconomyFetchedAt;

    @Column(name = "ebay_current_value_new")
    private BigDecimal ebayCurrentValueNew;

    @Column(name = "ebay_scanned_at")
    private Instant ebayScannedAt;

    @Column(name = "bricklink_current_value_new")
    private BigDecimal bricklinkCurrentValueNew;

    @Column(name = "bricklink_scanned_at")
    private Instant bricklinkScannedAt;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "ebay_search_query", columnDefinition = "TEXT")
    private String ebaySearchQuery;

    @Column(name = "ebay_exclude_words", columnDefinition = "TEXT")
    private String ebayExcludeWords;

    @Column(name = "ebay_feedback_min", nullable = false)
    private int ebayFeedbackMin = 1;

    @Column(name = "ebay_scan_interval_minutes", nullable = false)
    private int ebayScanIntervalMinutes = 15;

    @Column(name = "bricklink_scan_interval_minutes", nullable = false)
    private int bricklinkScanIntervalMinutes = 360;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SetWatch create(CatalogItem catalog) {
        SetWatch watch = new SetWatch();
        watch.id = UUID.randomUUID();
        watch.catalogItem = catalog;
        watch.createdAt = Instant.now();
        watch.updatedAt = Instant.now();
        watch.copyCatalog(catalog);
        return watch;
    }

    public void copyCatalog(CatalogItem catalog) {
        this.catalogItem = catalog;
        this.setNumber = catalog.getSetNumber();
        this.name = catalog.getName();
        this.theme = catalog.getTheme();
        this.subtheme = catalog.getSubtheme();
        this.releasedDate = catalog.getReleasedDate();
        this.retiredDate = catalog.getRetiredDate();
        this.retired = catalog.getRetired();
        this.piecesCount = catalog.getPiecesCount();
        this.minifigsCount = catalog.getMinifigsCount();
        this.retailPriceUs = catalog.getRetailPriceUs();
        this.currentValueNew = catalog.getCurrentValueNew();
        this.brickeconomyFetchedAt = catalog.getFetchedAt() != null ? catalog.getFetchedAt() : Instant.now();
    }

    public void recordEbayScan(BigDecimal avgPrice, Instant scannedAt) {
        this.ebayCurrentValueNew = avgPrice;
        this.ebayScannedAt = scannedAt;
        touch();
    }

    public void recordBrickLinkScan(BigDecimal avgPrice, Instant scannedAt) {
        this.bricklinkCurrentValueNew = avgPrice;
        this.bricklinkScannedAt = scannedAt;
        touch();
    }

    public void markScanned(Instant scannedAt) {
        this.lastScannedAt = scannedAt;
        touch();
    }

    public boolean isDue(Instant now) {
        return isEbayDue(now) || isBrickLinkDue(now);
    }

    public boolean isEbayDue(Instant now) {
        return isPlatformDue(now, ebayScannedAt, ebayScanIntervalMinutes);
    }

    public boolean isBrickLinkDue(Instant now) {
        return isPlatformDue(now, bricklinkScannedAt, bricklinkScanIntervalMinutes);
    }

    private boolean isPlatformDue(Instant now, Instant lastScan, int intervalMinutes) {
        if (!enabled) {
            return false;
        }
        if (lastScan == null) {
            return true;
        }
        return !now.isBefore(lastScan.plus(Duration.ofMinutes(intervalMinutes)));
    }

    public boolean acceptsPrice(BigDecimal price) {
        if (minPrice == null && maxPrice == null) {
            return true;
        }
        if (price == null) {
            return false;
        }
        if (minPrice != null && price.compareTo(minPrice) < 0) {
            return false;
        }
        return maxPrice == null || price.compareTo(maxPrice) <= 0;
    }

    public void setPriceRange(BigDecimal min, BigDecimal max) {
        if (min != null && min.compareTo(BigDecimal.ZERO) < 0) {
            min = BigDecimal.ZERO;
        }
        if (max != null && max.compareTo(BigDecimal.ZERO) < 0) {
            max = BigDecimal.ZERO;
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }
        this.minPrice = min;
        this.maxPrice = max;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public String getName() {
        return name;
    }

    public String getTheme() {
        return theme;
    }

    public String getSubtheme() {
        return subtheme;
    }

    public LocalDate getReleasedDate() {
        return releasedDate;
    }

    public LocalDate getRetiredDate() {
        return retiredDate;
    }

    public Boolean getRetired() {
        return retired;
    }

    public Integer getPiecesCount() {
        return piecesCount;
    }

    public Integer getMinifigsCount() {
        return minifigsCount;
    }

    public BigDecimal getRetailPriceUs() {
        return retailPriceUs;
    }

    public BigDecimal getCurrentValueNew() {
        return currentValueNew;
    }

    public Instant getBrickeconomyFetchedAt() {
        return brickeconomyFetchedAt;
    }

    public BigDecimal getEbayCurrentValueNew() {
        return ebayCurrentValueNew;
    }

    public Instant getEbayScannedAt() {
        return ebayScannedAt;
    }

    public BigDecimal getBricklinkCurrentValueNew() {
        return bricklinkCurrentValueNew;
    }

    public Instant getBricklinkScannedAt() {
        return bricklinkScannedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEbaySearchQuery() {
        return ebaySearchQuery;
    }

    public void setEbaySearchQuery(String ebaySearchQuery) {
        this.ebaySearchQuery = ebaySearchQuery;
    }

    public String getEbayExcludeWords() {
        return ebayExcludeWords;
    }

    public void setEbayExcludeWords(String ebayExcludeWords) {
        this.ebayExcludeWords = ebayExcludeWords;
    }

    public int getEbayFeedbackMin() {
        return ebayFeedbackMin;
    }

    public void setEbayFeedbackMin(int ebayFeedbackMin) {
        this.ebayFeedbackMin = Math.max(0, ebayFeedbackMin);
    }

    public int getEbayScanIntervalMinutes() {
        return ebayScanIntervalMinutes;
    }

    public void setEbayScanIntervalMinutes(int ebayScanIntervalMinutes) {
        this.ebayScanIntervalMinutes = clampScanInterval(ebayScanIntervalMinutes);
    }

    public int getBricklinkScanIntervalMinutes() {
        return bricklinkScanIntervalMinutes;
    }

    public void setBricklinkScanIntervalMinutes(int bricklinkScanIntervalMinutes) {
        this.bricklinkScanIntervalMinutes = clampScanInterval(bricklinkScanIntervalMinutes);
    }

    private static int clampScanInterval(int minutes) {
        return Math.min(1440, Math.max(5, minutes));
    }

    public Instant getLastScannedAt() {
        return lastScannedAt;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
