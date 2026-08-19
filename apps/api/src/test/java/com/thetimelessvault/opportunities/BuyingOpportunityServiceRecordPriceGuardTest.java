package com.thetimelessvault.opportunities;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.publish.ChannelListing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuyingOpportunityServiceRecordPriceGuardTest {

    @Test
    void usesEbayLiveUrl() {
        ChannelListing listing = listing(Platform.EBAY);
        listing.setLiveUrl("https://www.ebay.com/itm/123456789");

        assertEquals("https://www.ebay.com/itm/123456789", BuyingOpportunityService.listingUrl(listing));
    }

    @Test
    void replacesBrokenBrickLinkStoreUrlWithInventoryDetail() {
        ChannelListing listing = listing(Platform.BRICKLINK);
        listing.setLiveUrl("https://www.bricklink.com/v2/store.page?p=#/item?id=555589441");
        listing.setBricklinkPhotoUploadUrl("https://www.bricklink.com/v2/inventory_detail.page?invID=555589441");

        assertEquals(
                "https://www.bricklink.com/v2/inventory_detail.page?invID=555589441",
                BuyingOpportunityService.listingUrl(listing)
        );
    }

    @Test
    void fallsBackToInventoryPageWhenNoMarketplaceUrlExists() {
        ChannelListing listing = listing(Platform.EBAY);

        assertEquals("/inventory/" + listing.getInventoryItem().getId(), BuyingOpportunityService.listingUrl(listing));
    }

    private static ChannelListing listing(Platform platform) {
        CatalogItem catalog = CatalogItem.create("10195-1");
        catalog.setName("Republic Dropship with AT-OT Walker");
        InventoryItem item = InventoryItem.create(catalog, "SKU-10195");
        return ChannelListing.create(item, platform);
    }
}
