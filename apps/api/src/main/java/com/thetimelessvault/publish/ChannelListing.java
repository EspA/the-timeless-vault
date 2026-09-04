package com.thetimelessvault.publish;

import com.thetimelessvault.common.ListingStatus;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
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
@Table(name = "channel_listing")
public class ChannelListing {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem inventoryItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.DRAFT;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "live_url")
    private String liveUrl;

    @Column(name = "last_published_price")
    private BigDecimal lastPublishedPrice;

    @Column(name = "bricklink_photo_upload_url")
    private String bricklinkPhotoUploadUrl;

    @Column(name = "shopify_status")
    private String shopifyStatus;

    @Column(name = "bricklink_status")
    private String bricklinkStatus;

    @Column(name = "ebay_status")
    private String ebayStatus;

    @Column(name = "brickowl_status")
    private String brickowlStatus;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChannelListing create(InventoryItem item, Platform platform) {
        ChannelListing listing = new ChannelListing();
        listing.id = UUID.randomUUID();
        listing.inventoryItem = item;
        listing.platform = platform;
        listing.status = ListingStatus.DRAFT;
        listing.createdAt = Instant.now();
        listing.updatedAt = Instant.now();
        return listing;
    }

    public void markPublishing() {
        this.status = ListingStatus.PUBLISHING;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markPublished(String externalId, String liveUrl, BigDecimal price) {
        this.status = ListingStatus.PUBLISHED;
        this.externalId = externalId;
        this.liveUrl = liveUrl;
        this.lastPublishedPrice = price;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = ListingStatus.FAILED;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void markUpdated(String externalId, String liveUrl, BigDecimal price) {
        this.status = ListingStatus.PUBLISHED;
        if (externalId != null && !externalId.isBlank()) {
            this.externalId = externalId;
        }
        if (liveUrl != null && !liveUrl.isBlank()) {
            this.liveUrl = liveUrl;
        }
        this.lastPublishedPrice = price;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markUpdateFailed(String error) {
        this.status = ListingStatus.PUBLISHED;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public InventoryItem getInventoryItem() {
        return inventoryItem;
    }

    public Platform getPlatform() {
        return platform;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getLiveUrl() {
        return liveUrl;
    }

    public BigDecimal getLastPublishedPrice() {
        return lastPublishedPrice;
    }

    public String getShopifyStatus() {
        return shopifyStatus;
    }

    public void setShopifyStatus(String shopifyStatus) {
        this.shopifyStatus = shopifyStatus;
        this.updatedAt = Instant.now();
    }

    public String getBricklinkStatus() {
        return bricklinkStatus;
    }

    public void setBricklinkStatus(String bricklinkStatus) {
        this.bricklinkStatus = bricklinkStatus;
        this.updatedAt = Instant.now();
    }

    public String getEbayStatus() {
        return ebayStatus;
    }

    public void setEbayStatus(String ebayStatus) {
        this.ebayStatus = ebayStatus;
        this.updatedAt = Instant.now();
    }

    public String getBrickowlStatus() {
        return brickowlStatus;
    }

    public String visibilityStatus() {
        return switch (platform) {
            case EBAY -> ebayStatus;
            case BRICKLINK -> bricklinkStatus;
            case SHOPIFY -> shopifyStatus;
            case BRICKOWL -> brickowlStatus;
            case LOCAL -> null;
        };
    }

    public void setBrickowlStatus(String brickowlStatus) {
        this.brickowlStatus = brickowlStatus;
        this.updatedAt = Instant.now();
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
        this.updatedAt = Instant.now();
    }

    public void setLiveUrl(String liveUrl) {
        this.liveUrl = liveUrl;
        this.updatedAt = Instant.now();
    }

    public void markUnlisted() {
        switch (platform) {
            case SHOPIFY -> setShopifyStatus("UNLISTED");
            case BRICKLINK -> setBricklinkStatus("UNLISTED");
            case BRICKOWL -> setBrickowlStatus("UNLISTED");
            case EBAY -> setEbayStatus("UNLISTED");
            case LOCAL -> {
            }
        }
    }

    public String getBricklinkPhotoUploadUrl() {
        return bricklinkPhotoUploadUrl;
    }

    public void setBricklinkPhotoUploadUrl(String bricklinkPhotoUploadUrl) {
        this.bricklinkPhotoUploadUrl = bricklinkPhotoUploadUrl;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
