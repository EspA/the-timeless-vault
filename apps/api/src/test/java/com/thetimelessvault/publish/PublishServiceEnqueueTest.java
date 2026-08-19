package com.thetimelessvault.publish;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.ebay.EbayPublisher;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.shopify.ShopifyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishServiceEnqueueTest {

    @Mock InventoryService inventoryService;
    @Mock ChannelListingRepository listings;
    @Mock PublishJobRepository jobs;
    @Mock PublishWorker worker;
    @Mock ShopifyClient shopifyClient;
    @Mock BrickLinkClient brickLinkClient;
    @Mock EbayClient ebayClient;
    @Mock EbayPublisher ebayPublisher;
    @Mock PriceGuardRepository priceGuards;
    @Mock ListingLogService listingLogs;

    PublishService service;
    InventoryItem item;
    ChannelListing listing;

    @BeforeEach
    void setUp() {
        service = new PublishService(
                inventoryService,
                listings,
                jobs,
                worker,
                shopifyClient,
                brickLinkClient,
                ebayClient,
                ebayPublisher,
                priceGuards,
                listingLogs
        );
        CatalogItem catalog = CatalogItem.create("79001-1");
        item = InventoryItem.create(catalog, "SKU-79001");
        listing = ChannelListing.create(item, Platform.SHOPIFY);
    }

    @Test
    void createListingRejectedWhenChannelAlreadyHasListing() {
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(listing));

        ApiException error = assertThrows(
                ApiException.class,
                () -> service.enqueue(item.getId(), Set.of(Platform.SHOPIFY))
        );

        assertEquals("A Shopify listing has already been created for this item.", error.getMessage());
        verify(jobs, never()).save(any());
    }

    @Test
    void createListingRejectedWhenAnySelectedChannelExists() {
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(listing));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.EBAY))
                .thenReturn(Optional.empty());

        ApiException error = assertThrows(
                ApiException.class,
                () -> service.enqueue(item.getId(), Set.of(Platform.SHOPIFY, Platform.EBAY))
        );

        assertEquals("A Shopify listing has already been created for this item.", error.getMessage());
        verify(jobs, never()).save(any());
    }

    @Test
    void retryStillQueuesWhenListingExists() {
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY))
                .thenReturn(Optional.of(listing));
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PublishJob> queued = service.enqueueRetry(item.getId(), Platform.SHOPIFY);

        assertEquals(1, queued.size());
        verify(jobs).save(any());
    }

    @Test
    void updateQueuesExistingPublishedListings() {
        listing.markPublished("gid://shopify/Product/1", "https://shop.example/products/1", java.math.BigDecimal.TEN);
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of(listing));
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PublishJob> queued = service.enqueueUpdate(item.getId(), Set.of(Platform.SHOPIFY));

        assertEquals(1, queued.size());
        assertEquals(ListingAction.UPDATE, queued.getFirst().getAction());
    }

    @Test
    void updateRejectedWhenNoPublishedListingExists() {
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of());

        ApiException error = assertThrows(
                ApiException.class,
                () -> service.enqueueUpdate(item.getId(), Set.of())
        );

        assertEquals("No existing listings to update. Create a listing first.", error.getMessage());
        verify(jobs, never()).save(any());
    }

    @Test
    void alreadyCreatedMessageJoinsChannelNames() {
        assertEquals(
                "A Shopify listing has already been created for this item.",
                PublishService.alreadyCreatedMessage(List.of("Shopify"))
        );
        assertEquals(
                "Listings have already been created for Shopify and eBay.",
                PublishService.alreadyCreatedMessage(List.of("Shopify", "eBay"))
        );
        assertEquals(
                "Listings have already been created for Shopify, BrickLink, and eBay.",
                PublishService.alreadyCreatedMessage(List.of("Shopify", "BrickLink", "eBay"))
        );
    }
}
