package com.thetimelessvault.publish;

import com.thetimelessvault.alerts.PriceGuard;
import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.inventory.ChannelPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PublishWorker {

    private static final Logger log = LoggerFactory.getLogger(PublishWorker.class);

    private final InventoryService inventoryService;
    private final ChannelListingRepository listings;
    private final PublishJobRepository jobs;
    private final PriceGuardRepository priceGuards;
    private final Map<com.thetimelessvault.common.Platform, ChannelPublisher> publishers;

    public PublishWorker(
            InventoryService inventoryService,
            ChannelListingRepository listings,
            PublishJobRepository jobs,
            PriceGuardRepository priceGuards,
            List<ChannelPublisher> publisherList
    ) {
        this.inventoryService = inventoryService;
        this.listings = listings;
        this.jobs = jobs;
        this.priceGuards = priceGuards;
        this.publishers = publisherList.stream().collect(java.util.stream.Collectors.toMap(ChannelPublisher::platform, p -> p));
    }

    @Transactional
    public void run(UUID jobId) {
        PublishJob job = jobs.findById(jobId).orElseThrow();
        InventoryItem item = inventoryService.get(job.getInventoryItem().getId());
        ChannelListing listing = listings.findByInventoryItemIdAndPlatform(item.getId(), job.getPlatform())
                .orElseGet(() -> listings.save(ChannelListing.create(item, job.getPlatform())));
        job.start();
        listing.markPublishing();
        jobs.save(job);
        listings.save(listing);
        try {
            List<String> photos = inventoryService.photoUrls(item);
            PublishResult result = publishers.get(job.getPlatform()).publish(item, photos);
            listing.markPublished(result.externalId(), result.liveUrl(), ChannelPrice.amount(item, job.getPlatform()));
            if (result.bricklinkPhotoUploadUrl() != null) {
                listing.setBricklinkPhotoUploadUrl(result.bricklinkPhotoUploadUrl());
            }
            if (result.shopifyStatus() != null) {
                listing.setShopifyStatus(result.shopifyStatus());
            }
            if (result.bricklinkStatus() != null) {
                listing.setBricklinkStatus(result.bricklinkStatus());
            }
            if (result.ebayStatus() != null) {
                listing.setEbayStatus(result.ebayStatus());
            }
            listings.save(listing);
            priceGuards.findByChannelListingId(listing.getId())
                    .orElseGet(() -> priceGuards.save(PriceGuard.create(listing)));
            job.succeed();
            jobs.save(job);
        } catch (Exception e) {
            log.warn("Publish failed for {} on {}", item.getSku(), job.getPlatform(), e);
            String message = e instanceof ApiException api ? api.getMessage() : e.getMessage();
            listing.markFailed(message);
            listings.save(listing);
            job.fail(message);
            jobs.save(job);
        }
    }
}
