package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelPublisher;
import com.thetimelessvault.publish.PublishResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BrickLinkPublisher implements ChannelPublisher {

    private final BrickLinkClient client;

    public BrickLinkPublisher(BrickLinkClient client) {
        this.client = client;
    }

    @Override
    public Platform platform() {
        return Platform.BRICKLINK;
    }

    @Override
    public PublishResult publish(InventoryItem item, List<String> photoUrls) {
        JsonNode data = client.createInventory(item);
        String inventoryId = data.path("inventory_id").asText();
        String liveUrl = listingUrl(inventoryId);
        return PublishResult.bricklink(inventoryId, liveUrl, liveUrl, "UNLISTED");
    }

    @Override
    public PublishResult update(InventoryItem item, ChannelListing listing, List<String> photoUrls) {
        client.updateInventory(listing.getExternalId(), item);
        String liveUrl = listing.getLiveUrl();
        if (liveUrl == null || liveUrl.isBlank()) {
            liveUrl = listingUrl(listing.getExternalId());
        }
        String status = listing.getBricklinkStatus() == null ? "UNLISTED" : listing.getBricklinkStatus();
        return PublishResult.bricklink(listing.getExternalId(), liveUrl, liveUrl, status);
    }

    public static String listingUrl(String inventoryId) {
        if (inventoryId == null || inventoryId.isBlank()) {
            return null;
        }
        return "https://www.bricklink.com/v2/inventory_detail.page?invID=" + inventoryId.trim();
    }
}
