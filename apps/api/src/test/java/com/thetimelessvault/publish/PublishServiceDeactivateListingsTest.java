package com.thetimelessvault.publish;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishServiceDeactivateListingsTest {

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
    }

    @Test
    void unlistsActivePublishedListingsOnEveryChannel() {
        ChannelListing shopify = published(Platform.SHOPIFY, "gid://shopify/Product/1", "https://shop.example/1");
        shopify.setShopifyStatus("ACTIVE");
        ChannelListing bricklink = published(Platform.BRICKLINK, "12345", "https://bricklink.example/1");
        bricklink.setBricklinkStatus("ACTIVE");
        ChannelListing ebay = published(Platform.EBAY, "offer-1", "https://www.ebay.com/itm/999");
        ebay.setEbayStatus("ACTIVE");

        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of(shopify, bricklink, ebay));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.of(shopify));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK)).thenReturn(Optional.of(bricklink));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.EBAY)).thenReturn(Optional.of(ebay));
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(brickLinkClient.updateStockRoom("12345", true)).thenReturn(true);

        service.deactivatePublishedListings(item.getId());

        verify(shopifyClient).setProductStoreAvailability("gid://shopify/Product/1", false, 0);
        verify(brickLinkClient).updateStockRoom("12345", true);
        verify(ebayClient).withdrawOffer("offer-1");
        verify(ebayClient, never()).endListing(any());
    }

    @Test
    void endsImportedEbayListingWhenThereIsNoInventoryOffer() {
        ChannelListing ebay = published(Platform.EBAY, "365847291012", "https://www.ebay.com/itm/365847291012");
        ebay.setEbayStatus("ACTIVE");

        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of(ebay));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.EBAY)).thenReturn(Optional.of(ebay));
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ebayClient.findOfferId(item.getSku())).thenReturn(null);

        service.deactivatePublishedListings(item.getId());

        verify(ebayClient).endListing("365847291012");
        verify(ebayClient, never()).withdrawOffer(any());
        assertEquals("UNLISTED", ebay.getEbayStatus());
    }

    @Test
    void withdrawsImportedEbayListingWhenAnInventoryOfferIsFound() {
        ChannelListing ebay = published(Platform.EBAY, "365847291012", "https://www.ebay.com/itm/365847291012");
        ebay.setEbayStatus("ACTIVE");

        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of(ebay));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.EBAY)).thenReturn(Optional.of(ebay));
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ebayClient.findOfferId(item.getSku())).thenReturn("offer-imported");

        service.deactivatePublishedListings(item.getId());

        verify(ebayClient).withdrawOffer("offer-imported");
        verify(ebayClient, never()).endListing(any());
        assertEquals("offer-imported", ebay.getExternalId());
    }

    @Test
    void skipsDraftAndAlreadyInactiveListings() {
        ChannelListing draft = ChannelListing.create(item, Platform.SHOPIFY);
        ChannelListing inactive = published(Platform.EBAY, "offer-1", "https://www.ebay.com/itm/999");
        inactive.setEbayStatus("UNLISTED");

        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of(draft, inactive));

        service.deactivatePublishedListings(item.getId());

        verify(shopifyClient, never()).setProductStoreAvailability(any(), any(Boolean.class), any(Integer.class));
        verify(ebayClient, never()).withdrawOffer(any());
    }

    @Test
    void afterSaleSkipsRemoteUnlistOnSoldChannelButMarksItInactiveLocally() {
        ChannelListing shopify = published(Platform.SHOPIFY, "gid://shopify/Product/1", "https://shop.example/1");
        shopify.setShopifyStatus("ACTIVE");
        ChannelListing bricklink = published(Platform.BRICKLINK, "12345", "https://bricklink.example/1");
        bricklink.setBricklinkStatus("ACTIVE");
        ChannelListing ebay = published(Platform.EBAY, "offer-1", "https://www.ebay.com/itm/999");
        ebay.setEbayStatus("ACTIVE");

        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of(shopify, bricklink, ebay));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.of(shopify));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.BRICKLINK)).thenReturn(Optional.of(bricklink));
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(brickLinkClient.updateStockRoom("12345", true)).thenReturn(true);

        service.deactivatePublishedListingsAfterSale(item.getId(), Platform.EBAY);

        verify(shopifyClient).setProductStoreAvailability("gid://shopify/Product/1", false, 0);
        verify(brickLinkClient).updateStockRoom("12345", true);
        verify(ebayClient, never()).withdrawOffer(any());
        assertEquals("UNLISTED", ebay.getEbayStatus());
        verify(listingLogs).record(
                eq(item),
                eq(Platform.EBAY),
                eq(ListingAction.DEACTIVATE),
                eq(ListingLogStatus.SUCCESS),
                eq("Sold on this channel; listing marked inactive locally")
        );
    }

    @Test
    void afterSaleMarksChannelInactiveLocallyWhenRemoteUnlistFails() {
        ChannelListing shopify = published(Platform.SHOPIFY, "gid://shopify/Product/1", "https://shop.example/1");
        shopify.setShopifyStatus("ACTIVE");

        when(listings.findByInventoryItemId(item.getId())).thenReturn(List.of(shopify));
        when(listings.findByInventoryItemIdAndPlatform(item.getId(), Platform.SHOPIFY)).thenReturn(Optional.of(shopify));
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("already sold"))
                .when(shopifyClient).setProductStoreAvailability("gid://shopify/Product/1", false, 0);

        service.deactivatePublishedListingsAfterSale(item.getId(), Platform.EBAY);

        assertEquals("UNLISTED", shopify.getShopifyStatus());
        verify(listingLogs).record(
                eq(item),
                eq(Platform.SHOPIFY),
                eq(ListingAction.DEACTIVATE),
                eq(ListingLogStatus.SUCCESS),
                eq("Marked inactive locally after unlist failed: already sold")
        );
    }

    private ChannelListing published(Platform platform, String externalId, String liveUrl) {
        ChannelListing listing = ChannelListing.create(item, platform);
        listing.markPublished(externalId, liveUrl, new BigDecimal("99.00"));
        return listing;
    }
}
