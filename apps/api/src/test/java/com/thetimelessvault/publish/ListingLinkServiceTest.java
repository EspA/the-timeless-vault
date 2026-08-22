package com.thetimelessvault.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.shopify.ShopifyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListingLinkServiceTest {

    @Mock InventoryService inventoryService;
    @Mock ChannelListingRepository listings;
    @Mock EbayClient ebayClient;
    @Mock BrickLinkClient brickLinkClient;
    @Mock ShopifyClient shopifyClient;

    ListingLinkService service;
    InventoryItem item;

    @BeforeEach
    void setUp() {
        service = new ListingLinkService(inventoryService, listings, ebayClient, brickLinkClient, shopifyClient);
        item = InventoryItem.create(CatalogItem.create("7665"), "TTV-7665-AAAA");
        when(inventoryService.get(item.getId())).thenReturn(item);
        when(listings.findByPlatformAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(listings.findByPlatformAndLiveUrl(any(), any())).thenReturn(Optional.empty());
        when(listings.findByInventoryItemIdAndPlatform(any(), any())).thenReturn(Optional.empty());
        when(listings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void linksEbayListingByUrl() {
        when(ebayClient.findOfferId("TTV-7665-AAAA")).thenReturn(null);

        ChannelListing listing = service.link(item.getId(), Platform.EBAY, "https://www.ebay.com/itm/227311449843", false);

        assertEquals("227311449843", listing.getExternalId());
        assertEquals("https://www.ebay.com/itm/227311449843", listing.getLiveUrl());
        assertEquals("ACTIVE", listing.getEbayStatus());
    }

    @Test
    void linksBrickLinkLotAfterLookup() throws Exception {
        when(brickLinkClient.getInventory("525841562")).thenReturn(new ObjectMapper().readTree("""
                { "inventory_id": 525841562, "is_stock_room": false, "unit_price": "199.99" }
                """));

        ChannelListing listing = service.link(item.getId(), Platform.BRICKLINK, "525841562", false);

        assertEquals("525841562", listing.getExternalId());
        assertEquals("ACTIVE", listing.getBricklinkStatus());
        assertEquals(new java.math.BigDecimal("199.99"), listing.getLastPublishedPrice());
    }

    @Test
    void rejectsWhenAnotherItemAlreadyHasTheListing() {
        InventoryItem other = InventoryItem.create(CatalogItem.create("7665"), "TTV-7665-BBBB");
        ChannelListing existing = ChannelListing.create(other, Platform.EBAY);
        existing.markPublished("227311449843", "https://www.ebay.com/itm/227311449843", null);
        when(listings.findByPlatformAndExternalId(Platform.EBAY, "227311449843")).thenReturn(Optional.of(existing));
        when(ebayClient.findOfferId("TTV-7665-AAAA")).thenReturn(null);

        var error = assertThrows(
                com.thetimelessvault.common.ApiException.class,
                () -> service.link(item.getId(), Platform.EBAY, "227311449843", false)
        );
        assertEquals(409, error.getStatus().value());
    }

    @Test
    void movesListingWhenReplaceExisting() {
        InventoryItem other = InventoryItem.create(CatalogItem.create("7665"), "TTV-7665-BBBB");
        ChannelListing existing = ChannelListing.create(other, Platform.EBAY);
        existing.markPublished("227311449843", "https://www.ebay.com/itm/227311449843", null);
        when(listings.findByPlatformAndExternalId(Platform.EBAY, "227311449843")).thenReturn(Optional.of(existing));
        when(ebayClient.findOfferId("TTV-7665-AAAA")).thenReturn(null);

        ChannelListing listing = service.link(item.getId(), Platform.EBAY, "227311449843", true);

        verify(listings).delete(existing);
        assertEquals(item.getId(), listing.getInventoryItem().getId());
        assertEquals("227311449843", listing.getExternalId());
    }
}
