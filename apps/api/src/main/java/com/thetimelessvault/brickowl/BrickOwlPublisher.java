package com.thetimelessvault.brickowl;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelPublisher;
import com.thetimelessvault.publish.PublishResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BrickOwlPublisher implements ChannelPublisher {

    private final BrickOwlClient client;

    public BrickOwlPublisher(BrickOwlClient client) {
        this.client = client;
    }

    @Override
    public Platform platform() {
        return Platform.BRICKOWL;
    }

    @Override
    public PublishResult publish(InventoryItem item, List<String> photoUrls) {
        String boid = client.resolveSetBoid(item.getCatalogItem().getSetNumber());
        String lotId = client.createLot(item, boid);
        client.setForSale(lotId, false, item);
        JsonNode lot = client.getLot(lotId);
        return PublishResult.brickowl(lotId, BrickOwlClient.lotUrl(lot, lotId, boid), "UNLISTED");
    }

    @Override
    public PublishResult update(InventoryItem item, ChannelListing listing, List<String> photoUrls) {
        client.updateLot(listing.getExternalId(), item);
        JsonNode lot = client.getLot(listing.getExternalId());
        String liveUrl = listing.getLiveUrl();
        if (liveUrl == null || liveUrl.isBlank()) {
            liveUrl = BrickOwlClient.lotUrl(lot, listing.getExternalId(), BrickOwlClient.firstText(lot, "boid"));
        }
        String status = listing.getBrickowlStatus() == null ? "UNLISTED" : listing.getBrickowlStatus();
        return PublishResult.brickowl(listing.getExternalId(), liveUrl, status);
    }
}
