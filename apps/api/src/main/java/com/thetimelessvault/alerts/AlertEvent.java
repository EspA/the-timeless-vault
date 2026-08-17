package com.thetimelessvault.alerts;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
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
public class AlertEvent {

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

    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AlertEvent create(String type, String title, String dedupeKey) {
        AlertEvent event = new AlertEvent();
        event.id = UUID.randomUUID();
        event.type = type;
        event.title = title;
        event.dedupeKey = dedupeKey;
        event.createdAt = Instant.now();
        return event;
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
