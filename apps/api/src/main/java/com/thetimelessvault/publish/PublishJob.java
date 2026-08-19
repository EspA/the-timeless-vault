package com.thetimelessvault.publish;

import com.thetimelessvault.common.JobStatus;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publish_job")
public class PublishJob {

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
    private JobStatus status = JobStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingAction action = ListingAction.CREATE;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PublishJob queued(InventoryItem item, Platform platform) {
        return queued(item, platform, ListingAction.CREATE);
    }

    public static PublishJob queued(InventoryItem item, Platform platform, ListingAction action) {
        PublishJob job = new PublishJob();
        job.id = UUID.randomUUID();
        job.inventoryItem = item;
        job.platform = platform;
        job.status = JobStatus.QUEUED;
        job.action = action == null ? ListingAction.CREATE : action;
        job.createdAt = Instant.now();
        return job;
    }

    public void start() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void succeed() {
        this.status = JobStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.error = null;
    }

    public void fail(String error) {
        this.status = JobStatus.FAILED;
        this.finishedAt = Instant.now();
        this.error = error;
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

    public ListingAction getAction() {
        return action == null ? ListingAction.CREATE : action;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
