package com.thetimelessvault.publish;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing_log")
public class ListingLog {

    @Id
    private UUID id;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    private String sku;

    @Column(name = "set_number")
    private String setNumber;

    @Column(name = "item_title")
    private String itemTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingLogStatus status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    public static ListingLog create(
            InventoryItem item,
            Platform platform,
            ListingAction action,
            ListingLogStatus status,
            String message
    ) {
        ListingLog log = new ListingLog();
        log.id = UUID.randomUUID();
        log.inventoryItemId = item.getId();
        log.sku = item.getSku();
        CatalogItem catalog = item.getCatalogItem();
        if (catalog != null) {
            log.setNumber = catalog.getSetNumber();
        }
        log.itemTitle = item.getTitle();
        log.platform = platform;
        log.action = action;
        log.status = status;
        log.message = message;
        log.loggedAt = Instant.now();
        return log;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public String getSku() {
        return sku;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public Platform getPlatform() {
        return platform;
    }

    public ListingAction getAction() {
        return action;
    }

    public ListingLogStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }
}
