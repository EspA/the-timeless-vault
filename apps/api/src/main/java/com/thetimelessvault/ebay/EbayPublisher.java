package com.thetimelessvault.ebay;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.inventory.Photo;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelPublisher;
import com.thetimelessvault.publish.PublishResult;
import com.thetimelessvault.storage.ObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class EbayPublisher implements ChannelPublisher {

    private final EbayClient client;
    private final InventoryService inventory;
    private final ObjectStorage storage;

    public EbayPublisher(EbayClient client, InventoryService inventory, ObjectStorage storage) {
        this.client = client;
        this.inventory = inventory;
        this.storage = storage;
    }

    @Override
    public Platform platform() {
        return Platform.EBAY;
    }

    @Override
    public PublishResult publish(InventoryItem item, List<String> photoUrls) {
        if (!client.configured()) {
            throw ApiException.unavailable("eBay client credentials are not configured");
        }
        if (!client.sellReady()) {
            throw ApiException.unavailable("Connect eBay on the Settings page (OAuth refresh token missing)");
        }
        List<String> hostedPhotos = hostPhotos(item, photoUrls);
        if (hostedPhotos.isEmpty()) {
            throw ApiException.badRequest("eBay requires at least one photo");
        }
        client.createOrReplaceInventoryItem(item, hostedPhotos);
        String offerId = client.createOffer(item);
        String listingId = client.liveListingId(offerId);
        if (listingId != null) {
            return PublishResult.ebay(offerId, EbayClient.listingUrl(listingId), "ACTIVE");
        }
        return PublishResult.ebay(offerId, null, "UNLISTED");
    }

    @Override
    public PublishResult update(InventoryItem item, ChannelListing listing, List<String> photoUrls) {
        if (!client.configured()) {
            throw ApiException.unavailable("eBay client credentials are not configured");
        }
        if (!client.sellReady()) {
            throw ApiException.unavailable("Connect eBay on the Settings page (OAuth refresh token missing)");
        }
        List<String> hostedPhotos = hostPhotos(item, photoUrls);
        if (hostedPhotos.isEmpty()) {
            throw ApiException.badRequest("eBay requires at least one photo");
        }
        client.createOrReplaceInventoryItem(item, hostedPhotos);
        String offerId = client.createOffer(item);
        String listingId = client.liveListingId(offerId);
        String liveUrl = listingId != null ? EbayClient.listingUrl(listingId) : listing.getLiveUrl();
        String status = listing.getEbayStatus();
        if (status == null || status.isBlank()) {
            status = listingId != null ? "ACTIVE" : "UNLISTED";
        }
        return PublishResult.ebay(offerId, liveUrl, status);
    }

    public void syncInventory(InventoryItem item) {
        List<String> hostedPhotos = hostPhotos(item, List.of());
        if (hostedPhotos.isEmpty()) {
            throw ApiException.badRequest("eBay requires at least one photo");
        }
        client.createOrReplaceInventoryItem(item, hostedPhotos);
        client.createOffer(item);
    }

    private List<String> hostPhotos(InventoryItem item, List<String> photoUrls) {
        List<Photo> photos = inventory.photosFor(item.getId());
        List<String> hosted = new ArrayList<>();
        if (!photos.isEmpty()) {
            List<String> existing = client.existingImageUrls(item.getSku());
            if (existing.size() == photos.size()) {
                return existing;
            }
            for (Photo photo : photos) {
                try (InputStream in = storage.read(photo.getStorageKey())) {
                    EbayPictures.Prepared prepared = EbayPictures.ensureMinimum(in.readAllBytes(), photo.getContentType());
                    String publicUrl = storage.publicUrl(photo.getStorageKey());
                    if (!prepared.upscaled() && EbayClient.isPublicHttpsImageUrl(publicUrl)) {
                        hosted.add(publicUrl);
                    } else {
                        hosted.add(client.hostImage(prepared.bytes(), photo.getOriginalFilename(), prepared.contentType()));
                    }
                } catch (IOException | UncheckedIOException e) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not read photo for eBay upload");
                }
            }
            return hosted;
        }
        for (String url : photoUrls) {
            if (EbayClient.isPublicHttpsImageUrl(url)) {
                hosted.add(url);
            }
        }
        return hosted;
    }
}
