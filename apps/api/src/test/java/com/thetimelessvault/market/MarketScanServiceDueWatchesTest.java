package com.thetimelessvault.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.alerts.ListingAdjustmentService;
import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogItemRepository;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.settings.PriceGuardDefaults;
import com.thetimelessvault.settings.WatchDefaults;
import com.thetimelessvault.watch.SetWatch;
import com.thetimelessvault.watch.SetWatchRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketScanServiceDueWatchesTest {

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

    private CatalogItem catalog;
    private SetWatch watch;

    @BeforeEach
    void setUp() throws Exception {
        catalog = CatalogItem.create("79015");
        catalog.setName("Witch-King Battle");
        watch = SetWatch.create(catalog);
        watch.setEnabled(true);
        watch.setEbayScanIntervalMinutes(15);
        watch.setBricklinkScanIntervalMinutes(360);

        when(setWatches.findEnabledWithCatalog()).thenReturn(List.of(watch));
        when(setWatches.findByCatalogItemId(catalog.getId())).thenReturn(Optional.of(watch));
        when(setWatches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogItems.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshots.findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(marketListings.findByCatalogItemIdAndPlatformAndSnapshotScanTrigger(any(), any(), any()))
                .thenReturn(List.of());
        when(priceGuards.findEnabledWithListing()).thenReturn(List.of());
        when(scanLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(watchDefaults.excludeWords()).thenReturn("");
        when(ebayClient.browseConfigured()).thenReturn(true);
        when(ebayClient.searchBrowse(anyString())).thenReturn(
                new ObjectMapper().readTree("{\"itemSummaries\":[]}")
        );
        when(brickLinkClient.forSaleNewSealedShipsToUsa(anyString())).thenReturn(List.of());
    }

    @Test
    void scansBothPlatformsSeparatelyWhenNeitherHasBeenScanned() {
        service.scanDueWatches();

        verify(ebayClient).searchBrowse(anyString());
        verify(brickLinkClient).forSaleNewSealedShipsToUsa("79015");
        verify(opportunities, never()).recordScanFailure(any(), any(), any(), any());
    }

    @Test
    void scansOnlyEbayWhenOnlyEbayIntervalElapsed() {
        Instant now = Instant.now();
        watch.recordEbayScan(null, now.minus(Duration.ofMinutes(15)));
        watch.recordBrickLinkScan(null, now.minus(Duration.ofMinutes(10)));

        service.scanDueWatches();

        verify(ebayClient).searchBrowse(anyString());
        verify(brickLinkClient, never()).forSaleNewSealedShipsToUsa(anyString());
    }

    @Test
    void scansOnlyBrickLinkWhenOnlyBrickLinkIntervalElapsed() {
        Instant now = Instant.now();
        watch.recordEbayScan(null, now.minus(Duration.ofMinutes(5)));
        watch.recordBrickLinkScan(null, now.minus(Duration.ofMinutes(360)));

        service.scanDueWatches();

        verify(ebayClient, never()).searchBrowse(anyString());
        verify(brickLinkClient).forSaleNewSealedShipsToUsa("79015");
    }

    @Test
    void skipsBothPlatformsWhenNeitherIntervalElapsed() {
        Instant now = Instant.now();
        watch.recordEbayScan(null, now.minus(Duration.ofMinutes(5)));
        watch.recordBrickLinkScan(null, now.minus(Duration.ofMinutes(10)));

        service.scanDueWatches();

        verify(ebayClient, never()).searchBrowse(anyString());
        verify(brickLinkClient, never()).forSaleNewSealedShipsToUsa(anyString());
    }

    @Test
    void skipsEbayWhenBrowseIsNotConfigured() {
        when(ebayClient.browseConfigured()).thenReturn(false);

        service.scanDueWatches();

        verify(ebayClient, never()).searchBrowse(anyString());
        verify(brickLinkClient).forSaleNewSealedShipsToUsa("79015");
    }

    @Test
    void failedScanConsumesTheInterval() {
        when(ebayClient.searchBrowse(anyString())).thenThrow(new RuntimeException("eBay down"));

        service.scanDueWatches();
        service.scanDueWatches();

        verify(ebayClient).searchBrowse(anyString());
        verify(brickLinkClient).forSaleNewSealedShipsToUsa("79015");
        verify(opportunities).recordScanFailure(
                catalog, Platform.EBAY, "eBay down", ScanTrigger.AUTOMATIC);
    }

    @Test
    void rejectsCombinedScan() {
        assertThrows(ApiException.class, () -> service.scan(catalog.getId(), null));
        assertThrows(ApiException.class, () -> service.scan(catalog.getId(), Platform.SHOPIFY));
        verify(ebayClient, never()).searchBrowse(anyString());
        verify(brickLinkClient, never()).forSaleNewSealedShipsToUsa(anyString());
    }
}
