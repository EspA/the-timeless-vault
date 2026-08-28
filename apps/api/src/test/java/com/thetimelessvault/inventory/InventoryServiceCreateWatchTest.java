package com.thetimelessvault.inventory;

import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceCreateWatchTest {

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
    CatalogItem catalog;

    @BeforeEach
    void setUp() {
        service = new InventoryService(
                items, photos, listings, publishJobs, priceGuards, catalogService, storage, setWatches, marketScans, purchaseOrderLines
        );
        catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        when(catalogService.lookup("75192-1", false)).thenReturn(catalog);
        when(items.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void queuesAManualScanWhenAWatchIsCreated() {
        when(setWatches.ensureWatch(catalog, false)).thenReturn(true);

        service.create(request());

        verify(marketScans).scanBothManual(catalog.getId());
    }

    @Test
    void skipsWatchAndScanWhenTheSetIsAlreadyWatched() {
        when(setWatches.ensureWatch(catalog, false)).thenReturn(false);

        service.create(request());

        verify(marketScans, never()).scanBothManual(any());
    }

    private static InventoryDtos.CreateRequest request() {
        return new InventoryDtos.CreateRequest(
                "75192-1",
                "LEGO 75192",
                null,
                null,
                new BigDecimal("800"),
                new BigDecimal("800"),
                new BigDecimal("800"),
                new BigDecimal("800"),
                new BigDecimal("800"),
                1,
                null,
                ItemType.SET,
                ItemCondition.NEW_SEALED,
                null,
                List.of(),
                null,
                null,
                0,
                0,
                null,
                null,
                null,
                null
        );
    }
}
