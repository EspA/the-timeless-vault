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
    private final ListingLogService listingLogs;
    private final Map<com.thetimelessvault.common.Platform, ChannelPublisher> publishers;

    public PublishWorker(
            InventoryService inventoryService,
            ChannelListingRepository listings,
            PublishJobRepository jobs,
            PriceGuardRepository priceGuards,
            ListingLogService listingLogs,
            List<ChannelPublisher> publisherList
    ) {
        this.inventoryService = inventoryService;
        this.listings = listings;
        this.jobs = jobs;
        this.priceGuards = priceGuards;
        this.listingLogs = listingLogs;
        this.publishers = publisherList.stream().collect(java.util.stream.Collectors.toMap(ChannelPublisher::platform, p -> p));
    }

    @Transactional
    public void run(UUID jobId) {
        PublishJob job = jobs.findById(jobId).orElseThrow();
        InventoryItem item = inventoryService.get(job.getInventoryItem().getId());
        ChannelListing listing = listings.findByInventoryItemIdAndPlatform(item.getId(), job.getPlatform())
                .orElseGet(() -> listings.save(ChannelListing.create(item, job.getPlatform())));
        ListingAction action = job.getAction();
        job.start();
        listing.markPublishing();
        jobs.save(job);
        listings.save(listing);
        try {
            List<String> photos = inventoryService.photoUrls(item);
            ChannelPublisher publisher = publishers.get(job.getPlatform());
            PublishResult result = action == ListingAction.UPDATE
                    ? publisher.update(item, listing, photos)
                    : publisher.publish(item, photos);
            applyResult(listing, result, item, action);
            listings.save(listing);
            priceGuards.findByChannelListingId(listing.getId())
                    .orElseGet(() -> priceGuards.save(PriceGuard.create(listing)));
            job.succeed();
            jobs.save(job);
            listingLogs.record(
                    item,
                    job.getPlatform(),
                    action,
                    ListingLogStatus.SUCCESS,
                    successNote(action, result, listing)
            );
        } catch (Exception e) {
            log.warn("{} failed for {} on {}", action, item.getSku(), job.getPlatform(), e);
            String message = e instanceof ApiException api ? api.getMessage() : e.getMessage();
            if (action == ListingAction.UPDATE) {
                listing.markUpdateFailed(message);
            } else {
                listing.markFailed(message);
            }
            listings.save(listing);
            job.fail(message);
            jobs.save(job);
            listingLogs.record(item, job.getPlatform(), action, ListingLogStatus.FAILED, message);
        }
    }

    private void applyResult(ChannelListing listing, PublishResult result, InventoryItem item, ListingAction action) {
        var price = ChannelPrice.amount(item, listing.getPlatform());
        if (action == ListingAction.UPDATE) {
            listing.markUpdated(result.externalId(), result.liveUrl(), price);
        } else {
            listing.markPublished(result.externalId(), result.liveUrl(), price);
        }
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
    }

    private static String successNote(ListingAction action, PublishResult result, ChannelListing listing) {
        if (action == ListingAction.UPDATE) {
            String url = result.liveUrl() != null && !result.liveUrl().isBlank()
                    ? result.liveUrl()
                    : listing.getLiveUrl();
            return url != null && !url.isBlank() ? "Listing updated · " + url : "Listing updated";
        }
        return result.liveUrl() != null && !result.liveUrl().isBlank() ? result.liveUrl() : "Listing created";
    }
}
