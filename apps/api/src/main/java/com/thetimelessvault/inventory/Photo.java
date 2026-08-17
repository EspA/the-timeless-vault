package com.thetimelessvault.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photo")
public class Photo {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem inventoryItem;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "primary_for_bricklink", nullable = false)
    private boolean primaryForBricklink;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Photo create(InventoryItem item, String storageKey, String filename, String contentType, int sortOrder) {
        Photo photo = new Photo();
        photo.id = UUID.randomUUID();
        photo.inventoryItem = item;
        photo.storageKey = storageKey;
        photo.originalFilename = filename;
        photo.contentType = contentType;
        photo.sortOrder = sortOrder;
        photo.createdAt = Instant.now();
        return photo;
    }

    public UUID getId() {
        return id;
    }

    public InventoryItem getInventoryItem() {
        return inventoryItem;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isPrimaryForBricklink() {
        return primaryForBricklink;
    }

    public void setPrimaryForBricklink(boolean primaryForBricklink) {
        this.primaryForBricklink = primaryForBricklink;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
