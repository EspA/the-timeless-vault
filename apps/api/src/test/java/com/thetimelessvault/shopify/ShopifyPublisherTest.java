package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.publish.ChannelListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopifyPublisherTest {

    @Mock ShopifyClient client;

    ShopifyPublisher publisher;
    InventoryItem item;
    ChannelListing listing;

    @BeforeEach
    void setUp() {
        publisher = new ShopifyPublisher(client);
        CatalogItem catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        item = InventoryItem.create(catalog, "TTV-75192-1-AAAA");
        item.setTitle("LEGO 75192 Millennium Falcon");
        listing = ChannelListing.create(item, Platform.SHOPIFY);
        listing.setExternalId("gid://shopify/Product/1");
        listing.setLiveUrl("https://thetimelessvault.com/products/millennium-falcon");
    }

    @Test
    void quantityToPushFollowsVaultQuantityAfterRestock() {
        assertEquals(0, ShopifyPublisher.quantityToPush(item));
        item.applyStockAndQuantity(StockStatus.IN_STOCK, null);
        assertEquals(1, ShopifyPublisher.quantityToPush(item));
    }

    @Test
    void updatePushesRestockedQuantityEvenWhenListingIsUnlisted() {
        item.applyStockAndQuantity(StockStatus.IN_STOCK, null);
        listing.setShopifyStatus("UNLISTED");
        when(client.updateProduct(eq(listing.getExternalId()), eq(item), eq(List.of()), eq(1)))
                .thenReturn(product());

        publisher.update(item, listing, List.of());

        verify(client).updateProduct(listing.getExternalId(), item, List.of(), 1);
    }

    @Test
    void updateKeepsZeroWhenItemIsStillInTransit() {
        listing.setShopifyStatus("ACTIVE");
        when(client.updateProduct(eq(listing.getExternalId()), eq(item), eq(List.of()), eq(0)))
                .thenReturn(product());

        publisher.update(item, listing, List.of());

        verify(client).updateProduct(listing.getExternalId(), item, List.of(), 0);
    }

    private static ObjectNode product() {
        ObjectNode product = new ObjectMapper().createObjectNode();
        product.put("id", "gid://shopify/Product/1");
        product.put("handle", "millennium-falcon");
        product.put("status", "ACTIVE");
        product.put("onlineStoreUrl", "https://thetimelessvault.com/products/millennium-falcon");
        return product;
    }
}
