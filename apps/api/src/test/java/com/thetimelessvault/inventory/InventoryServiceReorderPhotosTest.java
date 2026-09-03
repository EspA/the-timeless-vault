package com.thetimelessvault.inventory;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.inbound.PurchaseOrderLineRepository;
import com.thetimelessvault.market.MarketScanLauncher;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.publish.PublishJobRepository;
import com.thetimelessvault.storage.ObjectStorage;
import com.thetimelessvault.watch.SetWatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceReorderPhotosTest {

    @Mock InventoryItemRepository items;
    @Mock PhotoRepository photos;
    @Mock ChannelListingRepository listings;
    @Mock PublishJobRepository publishJobs;
    @Mock PriceGuardRepository priceGuards;
    @Mock CatalogService catalogService;
    @Mock ObjectStorage storage;
    @Mock SetWatchService setWatches;
    @Mock MarketScanLauncher marketScans;
    @Mock PurchaseOrderLineRepository purchaseOrderLines;

    InventoryService service;
    InventoryItem item;
    Photo first;
    Photo second;
    Photo third;

    @BeforeEach
    void setUp() {
        service = new InventoryService(
                items, photos, listings, publishJobs, priceGuards, catalogService, storage, setWatches, marketScans, purchaseOrderLines
        );
        item = InventoryItem.create(CatalogItem.create("75192-1"), "TTV-75192-1");
        first = Photo.create(item, "one.jpg", "one.jpg", "image/jpeg", 0);
        second = Photo.create(item, "two.jpg", "two.jpg", "image/jpeg", 1);
        third = Photo.create(item, "three.jpg", "three.jpg", "image/jpeg", 2);
        when(items.findWithCatalogById(item.getId())).thenReturn(Optional.of(item));
    }

    @Test
    void assignsSortOrderFromTheRequestedIds() {
        when(photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(item.getId()))
                .thenReturn(List.of(first, second, third));
        when(photos.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Photo> ordered = service.reorderPhotos(item.getId(), List.of(third.getId(), first.getId(), second.getId()));

        assertEquals(List.of(third.getId(), first.getId(), second.getId()), ordered.stream().map(Photo::getId).toList());
        assertEquals(0, third.getSortOrder());
        assertEquals(1, first.getSortOrder());
        assertEquals(2, second.getSortOrder());
        verify(photos).saveAll(ordered);
    }

    @Test
    void rejectsAPartialOrUnknownOrder() {
        when(photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(item.getId()))
                .thenReturn(List.of(first, second));

        assertThrows(ApiException.class, () -> service.reorderPhotos(item.getId(), List.of(first.getId())));
        assertThrows(ApiException.class, () -> service.reorderPhotos(item.getId(), List.of(first.getId(), first.getId())));
        assertThrows(ApiException.class, () -> service.reorderPhotos(item.getId(), List.of(first.getId(), UUID.randomUUID())));
    }
}
