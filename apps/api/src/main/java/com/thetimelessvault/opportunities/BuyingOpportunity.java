package com.thetimelessvault.opportunities;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.market.ScanTrigger;
import com.thetimelessvault.publish.ChannelListing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_event")
public class BuyingOpportunity {

    public static final String TYPE_BUYING_OPPORTUNITY = "BUYING_OPPORTUNITY";
    public static final String TYPE_PRICE_HIGH = "PRICE_HIGH";
    public static final String TYPE_PRICE_LOW = "PRICE_LOW";
    public static final String TYPE_NEW_SALE = "NEW_SALE";
    public static final String TYPE_ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String TYPE_SCAN_FAILED = "SCAN_FAILED";

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_item_id")
    private CatalogItem catalogItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_listing_id")
    private ChannelListing channelListing;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    private Platform platform;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    private String url;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_trigger")
    private ScanTrigger scanTrigger;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static BuyingOpportunity create(String type, String title, String dedupeKey) {
        BuyingOpportunity opportunity = new BuyingOpportunity();
        opportunity.id = UUID.randomUUID();
        opportunity.type = type;
        opportunity.title = title;
        opportunity.dedupeKey = dedupeKey;
        opportunity.createdAt = Instant.now();
        return opportunity;
    }

    public UUID getId() {
        return id;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }

    public void setCatalogItem(CatalogItem catalogItem) {
        this.catalogItem = catalogItem;
    }

    public ChannelListing getChannelListing() {
        return channelListing;
    }

    public void setChannelListing(ChannelListing channelListing) {
        this.channelListing = channelListing;
    }

    public String getType() {
        return type;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public ScanTrigger getScanTrigger() {
        return scanTrigger;
    }

    public void setScanTrigger(ScanTrigger scanTrigger) {
        this.scanTrigger = scanTrigger;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getEmailedAt() {
        return emailedAt;
    }

    public void setEmailedAt(Instant emailedAt) {
        this.emailedAt = emailedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
