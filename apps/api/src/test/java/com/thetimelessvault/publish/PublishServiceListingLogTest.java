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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishServiceListingLogTest {

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
        catalog.setName("Escape from Mirkwood Spiders");
        item = InventoryItem.create(catalog, "SKU-79001");
        item.setTitle("LEGO 79001 Escape from Mirkwood Spiders");
        listing = ChannelListing.create(item, Platform.SHOPIFY);
        listing.markPublished("gid://shopify/Product/1", "https://shop.example/products/1", new BigDecimal("99.00"));
        listing.setShopifyStatus("ACTIVE");
        when(inventoryService.get(item.getId())).thenReturn(item);
    }

    @Test
    void activateSuccessIsLogged() {
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.of(listing));
        when(shopifyClient.updateProductStatus("gid://shopify/Product/1", "ACTIVE")).thenReturn("ACTIVE");
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.setShopifyStatus(item.getId(), "ACTIVE");

        verify(listingLogs).record(
                item,
                Platform.SHOPIFY,
                ListingAction.ACTIVATE,
                ListingLogStatus.SUCCESS,
                "Listing is now active"
        );
    }

    @Test
    void deactivateSuccessIsLogged() {
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.of(listing));
        when(shopifyClient.updateProductStatus("gid://shopify/Product/1", "UNLISTED")).thenReturn("UNLISTED");
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.setShopifyStatus(item.getId(), "UNLISTED");

        verify(listingLogs).record(
                item,
                Platform.SHOPIFY,
                ListingAction.DEACTIVATE,
                ListingLogStatus.SUCCESS,
                "Listing is now unlisted"
        );
    }

    @Test
    void activateFailureIsLogged() {
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.setShopifyStatus(item.getId(), "ACTIVE"));

        verify(listingLogs).record(
                item,
                Platform.SHOPIFY,
                ListingAction.ACTIVATE,
                ListingLogStatus.FAILED,
                "Shopify listing not found"
        );
        verify(listings, never()).save(any());
    }

    @Test
    void deleteSuccessIsLogged() {
        listing.setShopifyStatus("UNLISTED");
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.of(listing));
        when(priceGuards.findByChannelListingId(listing.getId())).thenReturn(Optional.empty());

        service.deleteShopifyListing(item.getId());

        verify(shopifyClient).deleteProduct("gid://shopify/Product/1");
        verify(listingLogs).record(
                item,
                Platform.SHOPIFY,
                ListingAction.DELETE,
                ListingLogStatus.SUCCESS,
                "Listing deleted"
        );
    }

    @Test
    void deleteFailureIsLoggedWhenStillActive() {
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.of(listing));

        assertThrows(ApiException.class, () -> service.deleteShopifyListing(item.getId()));

        verify(listingLogs).record(
                item,
                Platform.SHOPIFY,
                ListingAction.DELETE,
                ListingLogStatus.FAILED,
                "Make the Shopify listing inactive before deleting it."
        );
        verify(shopifyClient, never()).deleteProduct(any());
    }
}
