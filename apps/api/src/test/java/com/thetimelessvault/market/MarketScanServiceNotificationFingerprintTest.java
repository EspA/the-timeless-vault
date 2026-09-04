package com.thetimelessvault.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.alerts.ListingAdjustmentService;
import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogItemRepository;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.settings.PriceGuardDefaults;
import com.thetimelessvault.settings.WatchDefaults;
import com.thetimelessvault.watch.SetWatch;
import com.thetimelessvault.watch.SetWatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketScanServiceNotificationFingerprintTest {

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
        catalog = CatalogItem.create("79015-1");
        catalog.setName("Witch-King Battle");
        catalog.setTheme("The Hobbit");
        watch = SetWatch.create(catalog);
        watch.setEnabled(true);
        watch.setEbaySearchQuery("LEGO 79015");
        watch.setEbayExcludeWords("");

        when(setWatches.findByCatalogItemId(catalog.getId())).thenReturn(Optional.of(watch));
        when(setWatches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogItems.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshots.findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(marketListings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketListings.findBySnapshotId(any())).thenReturn(List.of());
        when(scanLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(watchDefaults.excludeWords()).thenReturn("");
        when(ebayClient.browseConfigured()).thenReturn(true);
        when(channelListings.findAllByStatusWithItem(any())).thenReturn(List.of());
        when(ebayClient.searchBrowse(anyString())).thenReturn(new ObjectMapper().readTree("""
                {
                  "itemSummaries": [{
                    "itemId": "v1|999|0",
                    "title": "LEGO 79015 Witch-King Battle",
                    "price": { "value": "150.00" },
                    "itemLocation": { "country": "US" },
                    "seller": { "username": "brickshop", "feedbackScore": 12 },
                    "itemWebUrl": "https://www.ebay.com/itm/999"
                  }]
                }
                """));
    }

    @Test
    void manualScanDoesNotCreateBuyingOpportunities() {
        when(marketListings.findByCatalogItemIdAndPlatformAndSnapshotScanTrigger(
                catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC
        )).thenReturn(List.of());

        service.scan(catalog.getId(), Platform.EBAY, ScanTrigger.MANUAL);

        verify(opportunities, never()).recordNewListing(any(), any(), any(), any(), any());
    }

    @Test
    void firstAutomaticScanAlertsForMatchingListings() {
        when(marketListings.findByCatalogItemIdAndPlatformAndSnapshotScanTrigger(
                catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC
        )).thenReturn(List.of());

        service.scan(catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC);

        verify(opportunities).recordNewListing(eq(catalog), eq(Platform.EBAY), any(), eq(watch), any());
    }

    @Test
    void automaticScanAlertsWhenListingWasOnlySeenOnAManualScan() {
        when(marketListings.findByCatalogItemIdAndPlatformAndSnapshotScanTrigger(
                catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC
        )).thenReturn(List.of(listing("v1|old-automatic|0")));

        service.scan(catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC);

        verify(opportunities).recordNewListing(eq(catalog), eq(Platform.EBAY), any(), eq(watch), any());
    }

    @Test
    void watchPriceRangeDoesNotChangeSnapshotStatistics() {
        watch.setPriceRange(new BigDecimal("1000"), new BigDecimal("2000"));
        when(marketListings.findByCatalogItemIdAndPlatformAndSnapshotScanTrigger(
                catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC
        )).thenReturn(List.of());
        ArgumentCaptor<MarketSnapshot> saved = ArgumentCaptor.forClass(MarketSnapshot.class);

        service.scan(catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC);

        verify(snapshots, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        MarketSnapshot snapshot = saved.getAllValues().getLast();
        assertEquals(0, new BigDecimal("150.00").compareTo(snapshot.getAvgPrice()));
        assertEquals(0, new BigDecimal("150.00").compareTo(snapshot.getMinPrice()));
        assertEquals(0, new BigDecimal("150.00").compareTo(snapshot.getMaxPrice()));
        assertEquals(1, snapshot.getListingCount());
        verify(marketListings).save(any(MarketListing.class));
    }

    @Test
    void doesNotAlertWhenAutomaticScanHasAlreadySeenTheListing() {
        when(marketListings.findByCatalogItemIdAndPlatformAndSnapshotScanTrigger(
                catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC
        )).thenReturn(List.of(listing("v1|999|0")));

        service.scan(catalog.getId(), Platform.EBAY, ScanTrigger.AUTOMATIC);

        verify(opportunities, never()).recordNewListing(any(), any(), any(), any(), any());
    }

    private MarketListing listing(String fingerprint) {
        MarketListing listing = MarketListing.create(
                MarketSnapshot.create(catalog, Platform.EBAY, "NEW", ScanTrigger.AUTOMATIC),
                catalog,
                Platform.EBAY
        );
        listing.setFingerprint(fingerprint);
        return listing;
    }
}
