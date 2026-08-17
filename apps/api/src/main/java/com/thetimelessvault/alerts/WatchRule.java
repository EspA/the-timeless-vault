package com.thetimelessvault.alerts;

import com.thetimelessvault.catalog.CatalogItem;
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
@Table(name = "watch_rule")
public class WatchRule {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id")
    private CatalogItem catalogItem;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "condition_filter", nullable = false)
    private String conditionFilter = "NEW";

    @Column(name = "max_price")
    private BigDecimal maxPrice;

    @Column(name = "title_keywords")
    private String titleKeywords;

    @Column(name = "ebay_search_query")
    private String ebaySearchQuery;

    @Column(name = "ebay_feedback_min", nullable = false)
    private int ebayFeedbackMin = 1;

    @Column(name = "ebay_exclude_words")
    private String ebayExcludeWords;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WatchRule create(CatalogItem catalog) {
        WatchRule rule = new WatchRule();
        rule.id = UUID.randomUUID();
        rule.catalogItem = catalog;
        rule.createdAt = Instant.now();
        rule.updatedAt = Instant.now();
        return rule;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConditionFilter() {
        return conditionFilter;
    }

    public void setConditionFilter(String conditionFilter) {
        this.conditionFilter = conditionFilter;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public String getTitleKeywords() {
        return titleKeywords;
    }

    public void setTitleKeywords(String titleKeywords) {
        this.titleKeywords = titleKeywords;
    }

    public String getEbaySearchQuery() {
        return ebaySearchQuery;
    }

    public void setEbaySearchQuery(String ebaySearchQuery) {
        this.ebaySearchQuery = ebaySearchQuery;
    }

    public int getEbayFeedbackMin() {
        return ebayFeedbackMin;
    }

    public void setEbayFeedbackMin(int ebayFeedbackMin) {
        this.ebayFeedbackMin = Math.max(0, ebayFeedbackMin);
    }

    public String getEbayExcludeWords() {
        return ebayExcludeWords;
    }

    public void setEbayExcludeWords(String ebayExcludeWords) {
        this.ebayExcludeWords = ebayExcludeWords;
    }
}
