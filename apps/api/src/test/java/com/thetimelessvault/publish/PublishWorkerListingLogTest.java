package com.thetimelessvault.publish;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ListingStatus;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishWorkerListingLogTest {

    @Mock InventoryService inventoryService;
    @Mock ChannelListingRepository listings;
    @Mock PublishJobRepository jobs;
    @Mock PriceGuardRepository priceGuards;
    @Mock ListingLogService listingLogs;
    @Mock ChannelPublisher publisher;

    PublishWorker worker;
    InventoryItem item;
    PublishJob job;
    ChannelListing listing;

    @BeforeEach
    void setUp() {
        when(publisher.platform()).thenReturn(Platform.EBAY);
        worker = new PublishWorker(inventoryService, listings, jobs, priceGuards, listingLogs, List.of(publisher));
        CatalogItem catalog = CatalogItem.create("79001-1");
        catalog.setName("Escape from Mirkwood Spiders");
        item = InventoryItem.create(catalog, "SKU-79001");
        item.setTitle("LEGO 79001 Escape from Mirkwood Spiders");
        item.setEbayPrice(new BigDecimal("120.00"));
        job = PublishJob.queued(item, Platform.EBAY);
        listing = ChannelListing.create(item, Platform.EBAY);
        when(jobs.findById(any())).thenAnswer(invocation -> Optional.of(job));
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.EBAY)).thenReturn(Optional.of(listing));
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryService.photoUrls(item)).thenReturn(List.of());
    }

    @Test
    void createListingSuccessIsLogged() {
        when(publisher.publish(item, List.of())).thenReturn(PublishResult.of("offer-1", "https://ebay.example/itm/1"));
        when(priceGuards.findByChannelListingId(listing.getId())).thenReturn(Optional.empty());

        worker.run(job.getId());

        verify(listingLogs).record(
                item,
                Platform.EBAY,
                ListingAction.CREATE,
                ListingLogStatus.SUCCESS,
                "https://ebay.example/itm/1"
        );
    }

    @Test
    void createListingFailureIsLogged() {
        when(publisher.publish(item, List.of())).thenThrow(new IllegalStateException("eBay rejected the offer"));

        worker.run(job.getId());

        verify(listingLogs).record(
                eq(item),
                eq(Platform.EBAY),
                eq(ListingAction.CREATE),
                eq(ListingLogStatus.FAILED),
                eq("eBay rejected the offer")
        );
    }

    @Test
    void updateListingSuccessIsLogged() {
        job = PublishJob.queued(item, Platform.EBAY, ListingAction.UPDATE);
        listing.markPublished("offer-1", "https://ebay.example/itm/1", new BigDecimal("120.00"));
        when(publisher.update(item, listing, List.of())).thenReturn(PublishResult.of("offer-1", "https://ebay.example/itm/1"));
        when(priceGuards.findByChannelListingId(listing.getId())).thenReturn(Optional.empty());

        worker.run(job.getId());

        verify(listingLogs).record(
                item,
                Platform.EBAY,
                ListingAction.UPDATE,
                ListingLogStatus.SUCCESS,
                "Listing updated · https://ebay.example/itm/1"
        );
        assertEquals(ListingStatus.PUBLISHED, listing.getStatus());
    }

    @Test
    void updateListingFailureKeepsPublishedListing() {
        job = PublishJob.queued(item, Platform.EBAY, ListingAction.UPDATE);
        listing.markPublished("offer-1", "https://ebay.example/itm/1", new BigDecimal("120.00"));
        when(publisher.update(item, listing, List.of())).thenThrow(new IllegalStateException("eBay rejected the revision"));

        worker.run(job.getId());

        verify(listingLogs).record(
                eq(item),
                eq(Platform.EBAY),
                eq(ListingAction.UPDATE),
                eq(ListingLogStatus.FAILED),
                eq("eBay rejected the revision")
        );
        assertEquals(ListingStatus.PUBLISHED, listing.getStatus());
        assertEquals("eBay rejected the revision", listing.getLastError());
    }
}
