package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelPublisher;
import com.thetimelessvault.publish.PublishResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShopifyPublisher implements ChannelPublisher {

    private final ShopifyClient client;

    public ShopifyPublisher(ShopifyClient client) {
        this.client = client;
    }

    @Override
    public Platform platform() {
        return Platform.SHOPIFY;
    }

    @Override
    public PublishResult publish(InventoryItem item, List<String> photoUrls) {
        String categoryId = client.resolveConstructionCategoryId();
        JsonNode product = client.createProduct(item, photoUrls, categoryId);
        String id = product.path("id").asText();
        String url = product.path("onlineStoreUrl").asText(null);
        if (url == null || url.isBlank()) {
            url = "https://thetimelessvault.com/products/" + product.path("handle").asText();
        }
        String status = product.path("status").asText("UNLISTED");
        return PublishResult.shopify(id, url, status);
    }

    @Override
    public PublishResult update(InventoryItem item, ChannelListing listing, List<String> photoUrls) {
        JsonNode product = client.updateProduct(listing.getExternalId(), item, photoUrls);
        String url = product.path("onlineStoreUrl").asText(null);
        if (url == null || url.isBlank()) {
            url = listing.getLiveUrl();
        }
        if (url == null || url.isBlank()) {
            url = "https://thetimelessvault.com/products/" + product.path("handle").asText();
        }
        String status = product.path("status").asText(listing.getShopifyStatus() == null ? "UNLISTED" : listing.getShopifyStatus());
        return PublishResult.shopify(listing.getExternalId(), url, status);
    }
}
