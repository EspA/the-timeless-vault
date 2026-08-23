package com.thetimelessvault.opportunities;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.PhotoRepository;
import com.thetimelessvault.market.MarketListing;
import com.thetimelessvault.market.MarketSnapshot;
import com.thetimelessvault.market.ScanTrigger;
import com.thetimelessvault.orders.ChannelOrder;
import com.thetimelessvault.orders.Order;
import com.thetimelessvault.orders.OrderRepository;
import com.thetimelessvault.orders.OrderStatus;
import com.thetimelessvault.settings.NotificationMailer;
import com.thetimelessvault.storage.ObjectStorage;
import com.thetimelessvault.watch.SetWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyingOpportunityServiceRecordNewListingTest {

    @Mock BuyingOpportunityRepository opportunities;
    @Mock OrderRepository orders;
    @Mock NotificationMailer notificationMailer;
    @Mock PhotoRepository photos;
    @Mock ObjectStorage storage;

    BuyingOpportunityService service;

    private CatalogItem catalog;
    private SetWatch watch;
    private MarketListing listing;
    private String dedupe;

    @BeforeEach
    void setUp() {
        service = new BuyingOpportunityService(
                opportunities,
                orders,
                notificationMailer,
                new AppProperties(),
                photos,
                storage
        );
        catalog = CatalogItem.create("75017-1");
        catalog.setName("Duel on Geonosis");
        watch = SetWatch.create(catalog);
        watch.setEnabled(true);
        listing = MarketListing.create(
                MarketSnapshot.create(catalog, Platform.EBAY, "NEW", ScanTrigger.AUTOMATIC),
                catalog,
                Platform.EBAY
        );
        listing.setFingerprint("v1|146877641523|0");
        listing.setTitle("LEGO Star Wars: Duel on Geonosis (75017) new still sealed");
        listing.setPrice(new BigDecimal("315.00"));
        listing.setUrl("https://www.ebay.com/itm/146877641523");
        listing.setSeller("brickshop");
        listing.setSellerFeedbackScore(1842);
        listing.setSellerFeedbackPercentage("99.8");
        dedupe = "NEW:EBAY:" + catalog.getId() + ":v1|146877641523|0";
    }

    @Test
    void firstAutomaticSightingCreatesAnOpportunityEvenWhenAManualOneAlreadyExists() {
        when(opportunities.findByDedupeKeyAndScanTrigger(dedupe, ScanTrigger.AUTOMATIC)).thenReturn(Optional.empty());
        when(opportunities.save(any(BuyingOpportunity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationMailer.sendQuietly(any(), any(), any())).thenReturn(false);

        service.recordNewListing(catalog, Platform.EBAY, listing, watch);

        ArgumentCaptor<BuyingOpportunity> saved = ArgumentCaptor.forClass(BuyingOpportunity.class);
        verify(opportunities).save(saved.capture());
        assertEquals(BuyingOpportunity.TYPE_BUYING_OPPORTUNITY, saved.getValue().getType());
        assertEquals(ScanTrigger.AUTOMATIC, saved.getValue().getScanTrigger());
        assertEquals(dedupe, saved.getValue().getDedupeKey());
    }

    @Test
    void sellerMetaIsFeedbackOnEbayAndCountryOnBrickLink() {
        listing.setSellerCountry("United Kingdom");
        assertEquals(
                "Feedback 1,842 / 99.8%",
                BuyingOpportunityService.sellerMeta(Platform.EBAY, listing)
        );
        assertEquals(
                "Country United Kingdom",
                BuyingOpportunityService.sellerMeta(Platform.BRICKLINK, listing)
        );
    }

    @Test
    void emailsHtmlWithSetNamePriceAndPercentVsMedian() {
        listing.setImageUrl("https://i.ebayimg.com/photo.jpg");
        when(opportunities.findByDedupeKeyAndScanTrigger(dedupe, ScanTrigger.AUTOMATIC)).thenReturn(Optional.empty());
        when(opportunities.save(any(BuyingOpportunity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationMailer.sendQuietly(any(), any(), any())).thenReturn(true);

        service.recordNewListing(catalog, Platform.EBAY, listing, watch, new BigDecimal("358.00"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notificationMailer).sendQuietly(subject.capture(), any(), html.capture());
        assertTrue(subject.getValue().contains("BUYING OPPORTUNITY"));
        assertTrue(html.getValue().contains("75017-1 / Duel on Geonosis"));
        assertTrue(html.getValue().contains("$315.00"));
        assertTrue(html.getValue().contains("(-12%)"));
        assertTrue(html.getValue().contains("https://www.ebay.com/itm/146877641523"));
        assertTrue(html.getValue().contains("https://i.ebayimg.com/photo.jpg"));
        assertTrue(html.getValue().contains("Scanned"));
        assertTrue(html.getValue().contains("brickshop"));
        assertTrue(html.getValue().contains("Feedback 1,842 / 99.8%"));
        assertTrue(html.getValue().contains("ebay-logo.png"));
        assertTrue(subject.getValue().contains("eBay"));
    }

    @Test
    void skipsListingsOutsideTheWatchAlertPriceRange() {
        watch.setPriceRange(new BigDecimal("400"), new BigDecimal("500"));

        service.recordNewListing(catalog, Platform.EBAY, listing, watch);

        verify(opportunities, never()).save(any());
        verify(opportunities, never()).findByDedupeKeyAndScanTrigger(any(), any());
    }

    @Test
    void doesNotCreateWhenAnAutomaticOpportunityAlreadyExists() {
        BuyingOpportunity existing = BuyingOpportunity.create(BuyingOpportunity.TYPE_BUYING_OPPORTUNITY, "already seen", dedupe);
        existing.setScanTrigger(ScanTrigger.AUTOMATIC);
        when(opportunities.findByDedupeKeyAndScanTrigger(dedupe, ScanTrigger.AUTOMATIC)).thenReturn(Optional.of(existing));

        service.recordNewListing(catalog, Platform.EBAY, listing, watch);

        verify(opportunities, never()).save(any());
    }

    @Test
    void deleteNewListingsRemovesWatchHistoryOnly() {
        service.deleteNewListings(catalog.getId());
        verify(opportunities).deleteByCatalogItem_IdAndType(catalog.getId(), BuyingOpportunity.TYPE_BUYING_OPPORTUNITY);
    }

    @Test
    void recordNewSaleCreatesANewSaleNotification() {
        InventoryItem item = InventoryItem.create(catalog, "TTV-75017-1-AAAA");
        Order sale = Order.create(item, new ChannelOrder(
                Platform.EBAY,
                "12-345",
                "li-1",
                item.getSku(),
                null,
                "LEGO 75017 Duel on Geonosis",
                "75017-1",
                1,
                new BigDecimal("315.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z"),
                "https://www.ebay.com/sh/ord/details?orderid=12-345",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderStatus.OPEN,
                null,
                null
        ), false);
        when(opportunities.findByDedupeKey("SALE:EBAY:12-345:li-1")).thenReturn(Optional.empty());
        when(opportunities.save(any(BuyingOpportunity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(photos.findByInventoryItemIdOrderBySortOrderAscCreatedAtAsc(item.getId())).thenReturn(List.of());
        when(notificationMailer.sendQuietly(any(), any(), any())).thenReturn(false);

        service.recordNewSale(sale, item);

        ArgumentCaptor<BuyingOpportunity> saved = ArgumentCaptor.forClass(BuyingOpportunity.class);
        verify(opportunities).save(saved.capture());
        assertEquals(BuyingOpportunity.TYPE_NEW_SALE, saved.getValue().getType());
        assertEquals("SALE:EBAY:12-345:li-1", saved.getValue().getDedupeKey());
        assertEquals("New eBay sale of 75017-1 Duel on Geonosis", saved.getValue().getTitle());
        assertEquals("/orders/" + sale.getId(), saved.getValue().getUrl());
        assertTrue(saved.getValue().getBody().contains("$315.00"));
        assertTrue(saved.getValue().getBody().contains("order 12-345"));
    }

    @Test
    void displayUrlRewritesExistingNewSaleToOrderPage() {
        InventoryItem item = InventoryItem.create(catalog, "TTV-75017-1-AAAA");
        Order sale = Order.create(item, new ChannelOrder(
                Platform.EBAY,
                "12-345",
                "li-1",
                item.getSku(),
                null,
                "LEGO 75017 Duel on Geonosis",
                "75017-1",
                1,
                new BigDecimal("315.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z"),
                "https://www.ebay.com/sh/ord/details?orderid=12-345",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderStatus.OPEN,
                null,
                null
        ), false);
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_NEW_SALE,
                "New eBay sale",
                "SALE:EBAY:12-345:li-1"
        );
        opportunity.setUrl("https://www.ebay.com/sh/ord/details?orderid=12-345");
        when(orders.findByPlatformAndExternalOrderIdAndExternalLineId(Platform.EBAY, "12-345", "li-1"))
                .thenReturn(Optional.of(sale));

        assertEquals("/orders/" + sale.getId(), service.displayUrl(opportunity));
    }

    @Test
    void recordNewSaleSkipsDuplicateOrderLines() {
        InventoryItem item = InventoryItem.create(catalog, "TTV-75017-1-AAAA");
        Order sale = Order.create(item, new ChannelOrder(
                Platform.EBAY,
                "12-345",
                "li-1",
                item.getSku(),
                null,
                "LEGO 75017 Duel on Geonosis",
                "75017-1",
                1,
                new BigDecimal("315.00"),
                "USD",
                Instant.parse("2026-08-21T12:00:00Z"),
                "https://www.ebay.com/sh/ord/details?orderid=12-345",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderStatus.OPEN,
                null,
                null
        ), false);
        when(opportunities.findByDedupeKey("SALE:EBAY:12-345:li-1"))
                .thenReturn(Optional.of(BuyingOpportunity.create(BuyingOpportunity.TYPE_NEW_SALE, "already", "SALE:EBAY:12-345:li-1")));

        service.recordNewSale(sale, item);

        verify(opportunities, never()).save(any());
    }

    @Test
    void recordScanFailureCreatesNotificationAndEmail() {
        when(opportunities.findByDedupeKey(org.mockito.ArgumentMatchers.startsWith("SCAN_FAIL:EBAY:")))
                .thenReturn(Optional.empty());
        when(opportunities.save(any(BuyingOpportunity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationMailer.sendQuietly(any(), any(), any())).thenReturn(false);

        service.recordScanFailure(catalog, Platform.EBAY, "eBay search failed.", ScanTrigger.AUTOMATIC);

        ArgumentCaptor<BuyingOpportunity> saved = ArgumentCaptor.forClass(BuyingOpportunity.class);
        verify(opportunities).save(saved.capture());
        assertEquals(BuyingOpportunity.TYPE_SCAN_FAILED, saved.getValue().getType());
        assertEquals(ScanTrigger.AUTOMATIC, saved.getValue().getScanTrigger());
        assertEquals("eBay search failed.", saved.getValue().getBody());
        assertEquals("/market/" + catalog.getId(), saved.getValue().getUrl());
        assertTrue(saved.getValue().getTitle().contains("eBay"));
        assertTrue(saved.getValue().getTitle().contains("75017-1"));
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(notificationMailer).sendQuietly(subject.capture(), any(), html.capture());
        assertTrue(subject.getValue().contains("SCAN FAILED"));
        assertTrue(html.getValue().contains("eBay search failed."));
    }

    @Test
    void recordScanFailureSkipsManualScans() {
        service.recordScanFailure(catalog, Platform.EBAY, "eBay search failed.", ScanTrigger.MANUAL);

        verify(opportunities, never()).save(any());
        verify(notificationMailer, never()).sendQuietly(any(), any(), any());
    }

    @Test
    void recordScanFailureSkipsDuplicateSameDay() {
        when(opportunities.findByDedupeKey(org.mockito.ArgumentMatchers.startsWith("SCAN_FAIL:EBAY:")))
                .thenReturn(Optional.of(BuyingOpportunity.create(
                        BuyingOpportunity.TYPE_SCAN_FAILED, "already", "SCAN_FAIL:EBAY:x")));

        service.recordScanFailure(catalog, Platform.EBAY, "eBay search failed.", ScanTrigger.AUTOMATIC);

        verify(opportunities, never()).save(any());
    }
}
