package com.thetimelessvault.inventory;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.inbound.PurchaseOrderLineRepository;
import com.thetimelessvault.inbound.PurchaseOrderStatus;
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
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServicePurchaseOrderGuardTest {

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

    @BeforeEach
    void setUp() {
        service = new InventoryService(
                items, photos, listings, publishJobs, priceGuards, catalogService, storage, setWatches, marketScans, purchaseOrderLines
        );
    }

    @Test
    void deleteIsBlockedWhenSkuIsOnAnInTransitPurchaseOrder() {
        UUID itemId = UUID.randomUUID();
        when(purchaseOrderLines.existsByInventoryItemIdAndPurchaseOrder_StatusIn(
                itemId, java.util.List.of(PurchaseOrderStatus.IN_TRANSIT, PurchaseOrderStatus.DELIVERED)))
                .thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () -> service.delete(itemId));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(items, never()).delete(any(InventoryItem.class));
    }
}
