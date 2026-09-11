package com.thetimelessvault.market;

import com.thetimelessvault.alerts.ListingAdjustmentService;
import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogItemRepository;
import com.thetimelessvault.common.ListingStatus;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.opportunities.BuyingOpportunity;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.settings.PriceGuardDefaults;
import com.thetimelessvault.settings.WatchDefaults;
import com.thetimelessvault.watch.SetWatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketScanServicePriceGuardsTest {

    @Mock SetWatchRepository setWatches;
    @Mock CatalogItemRepository catalogItems;
    @Mock MarketSnapshotRepository snapshots;
    @Mock MarketListingRepository marketListings;
    @Mock ChannelListingRepository channelListings;
    @Mock PriceGuardRepository priceGuards;
    @Mock ListingAdjustmentService listingAdjustments;
    @Mock EbayClient ebayClient;
    @Mock BrickLinkClient brickLinkClient;
    @Mock BuyingOpportunityService opportunities;
    @Mock ScanLogRepository scanLogs;
    @Mock WatchDefaults watchDefaults;
    @Mock PriceGuardDefaults priceGuardDefaults;
    @Mock ObjectProvider<MarketScanService> self;

    @InjectMocks
    MarketScanService service;

    private ChannelListing listing;
    private InventoryItem item;
    private MarketSnapshot snapshot;

    @BeforeEach
    void setUp() {
        CatalogItem catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        item = InventoryItem.create(catalog, "TTV-75192-7C79");
        item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);
        listing = ChannelListing.create(item, Platform.EBAY);
        listing.markPublished("227222227698", "https://www.ebay.com/itm/227222227698", new BigDecimal("1239.00"));

        snapshot = MarketSnapshot.create(catalog, Platform.EBAY, "NEW", ScanTrigger.AUTOMATIC);
        snapshot.setMedianPrice(new BigDecimal("999.99"));
        snapshot.setAvgPrice(new BigDecimal("1053.30"));

        when(setWatches.findEnabledWithCatalog()).thenReturn(List.of());
        when(priceGuardDefaults.highPercent()).thenReturn(new BigDecimal("15.00"));
        when(priceGuardDefaults.lowPercent()).thenReturn(new BigDecimal("5.00"));
        when(priceGuards.findDisabledListingIds()).thenReturn(List.of());
        when(channelListings.findAllByStatusWithItem(ListingStatus.PUBLISHED)).thenReturn(List.of(listing));
        when(snapshots.findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(
                catalog.getId(), Platform.EBAY, "NEW"
        )).thenReturn(Optional.of(snapshot));
    }

    @Test
    void recordsPriceHighForPublishedListingWithoutAPriceGuardRow() {
        service.scanDueWatches();

        verify(listingAdjustments).recordOutOfRange(
                eq(listing),
                eq(BuyingOpportunity.TYPE_PRICE_HIGH),
                eq(new BigDecimal("1239.00")),
                eq(new BigDecimal("999.99")),
                eq(new BigDecimal("15.00")),
                eq(new BigDecimal("5.00")),
                eq(snapshot.getScannedAt())
        );
    }

    @Test
    void skipsDisabledPriceGuard() {
        when(priceGuards.findDisabledListingIds()).thenReturn(List.of(listing.getId()));

        service.scanDueWatches();

        verify(listingAdjustments).resolveInRange(listing);
        verify(listingAdjustments, never()).recordOutOfRange(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsSoldInventory() {
        item.applyStockAndQuantity(StockStatus.SOLD, 0);

        service.scanDueWatches();

        verify(listingAdjustments).resolveInRange(listing);
        verify(listingAdjustments, never()).recordOutOfRange(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsUnlistedListing() {
        listing.setEbayStatus("UNLISTED");

        service.scanDueWatches();

        verify(listingAdjustments).resolveInRange(listing);
        verify(listingAdjustments, never()).recordOutOfRange(any(), any(), any(), any(), any(), any(), any());
    }
}
