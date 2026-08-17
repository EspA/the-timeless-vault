package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
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
        String liveUrl = "https://www.bricklink.com/v2/store.page?p=#/item?id=" + inventoryId;
        String photoUrl = "https://www.bricklink.com/v2/inventory_detail.page?invID=" + inventoryId;
        return PublishResult.bricklink(inventoryId, liveUrl, photoUrl, "UNLISTED");
    }
}
