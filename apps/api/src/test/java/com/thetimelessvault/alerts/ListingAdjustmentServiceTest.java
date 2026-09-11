package com.thetimelessvault.alerts;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.opportunities.BuyingOpportunity;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.publish.ChannelListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingAdjustmentServiceTest {

    @Mock ListingAdjustmentRepository adjustments;
    @Mock BuyingOpportunityService opportunities;

    @InjectMocks
    ListingAdjustmentService service;

    private ChannelListing listing;
    private Instant scannedAt;
    private Instant now;

    @BeforeEach
    void setUp() {
        listing = listing(Platform.EBAY);
        listing.markPublished("123", "https://www.ebay.com/itm/123", new BigDecimal("200"));
        listing.setEbayStatus("ACTIVE");
        scannedAt = Instant.parse("2026-09-03T16:00:00Z");
        now = Instant.parse("2026-09-03T16:05:00Z");
        org.mockito.Mockito.lenient().when(adjustments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstDetectionCreatesRowAndNotifies() {
        when(adjustments.findByChannelListingId(listing.getId())).thenReturn(Optional.empty());

        service.recordOutOfRange(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"),
                new BigDecimal("15"), new BigDecimal("15"),
                scannedAt, now);

        ArgumentCaptor<ListingAdjustment> captor = ArgumentCaptor.forClass(ListingAdjustment.class);
        verify(adjustments).save(captor.capture());
        ListingAdjustment row = captor.getValue();
        assertEquals(BuyingOpportunity.TYPE_PRICE_HIGH, row.getType());
        assertTrue(row.isActive());
        assertEquals(scannedAt, row.getDetectedAt());
        verify(opportunities).recordPriceGuard(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"),
                new BigDecimal("15"), new BigDecimal("15"),
                scannedAt);
    }

    @Test
    void stillOutOfRangeDoesNotNotifyAgain() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        when(adjustments.findByChannelListingId(listing.getId())).thenReturn(Optional.of(existing));

        service.recordOutOfRange(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("210"), new BigDecimal("155"),
                new BigDecimal("15"), new BigDecimal("15"),
                scannedAt.plusSeconds(3600), now.plusSeconds(3600));

        verify(opportunities, never()).recordPriceGuard(any(), any(), any(), any(), any(), any(), any());
        assertTrue(existing.isActive());
        assertEquals(0, new BigDecimal("210").compareTo(existing.getYourPrice()));
    }

    @Test
    void dismissHidesActiveRow() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        when(adjustments.findByIdWithListing(existing.getId())).thenReturn(Optional.of(existing));

        ListingAdjustmentService.ListingAdjustmentView dismissed = service.dismiss(existing.getId());

        assertTrue(existing.isDismissed());
        assertEquals(BuyingOpportunity.TYPE_PRICE_HIGH, dismissed.type());
        verify(adjustments).save(existing);
    }

    @Test
    void dismissedSameDayDoesNotComeBack() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        existing.dismiss(now);
        when(adjustments.findByChannelListingId(listing.getId())).thenReturn(Optional.of(existing));

        service.recordOutOfRange(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"),
                new BigDecimal("15"), new BigDecimal("15"),
                scannedAt, now.plusSeconds(60));

        verify(opportunities, never()).recordPriceGuard(any(), any(), any(), any(), any(), any(), any());
        assertTrue(existing.isDismissed());
    }

    @Test
    void dismissedPriorDayComesBackWithoutNotification() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        Instant dismissedAt = ZonedDateTime.of(LocalDate.of(2026, 9, 2).atTime(20, 0), ListingAdjustment.BUSINESS_ZONE)
                .toInstant();
        existing.dismiss(dismissedAt);
        Instant nextDay = ZonedDateTime.of(LocalDate.of(2026, 9, 3).atTime(8, 0), ListingAdjustment.BUSINESS_ZONE)
                .toInstant();
        when(adjustments.findByChannelListingId(listing.getId())).thenReturn(Optional.of(existing));

        service.recordOutOfRange(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"),
                new BigDecimal("15"), new BigDecimal("15"),
                nextDay, nextDay);

        verify(opportunities, never()).recordPriceGuard(any(), any(), any(), any(), any(), any(), any());
        assertTrue(existing.isActive());
        assertNull(existing.getDismissedAt());
        assertEquals(nextDay, existing.getDetectedAt());
    }

    @Test
    void inRangeResolvesSoItDoesNotComeBack() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        existing.dismiss(now);
        when(adjustments.findByChannelListingId(listing.getId())).thenReturn(Optional.of(existing));

        service.resolveInRange(listing, now.plusSeconds(30));

        assertTrue(existing.isResolved());
        verify(adjustments).save(existing);
    }

    @Test
    void resolvedThenOutOfRangeAgainNotifies() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        existing.resolve(now);
        when(adjustments.findByChannelListingId(listing.getId())).thenReturn(Optional.of(existing));

        service.recordOutOfRange(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("220"), new BigDecimal("150"),
                new BigDecimal("15"), new BigDecimal("15"),
                scannedAt.plusSeconds(86_400), now.plusSeconds(86_400));

        verify(opportunities, times(1)).recordPriceGuard(any(), any(), any(), any(), any(), any(), any());
        assertTrue(existing.isActive());
        assertEquals(BuyingOpportunity.TYPE_PRICE_HIGH, existing.getType());
    }

    @Test
    void typeChangeNotifiesAsNewAlert() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        when(adjustments.findByChannelListingId(listing.getId())).thenReturn(Optional.of(existing));

        service.recordOutOfRange(
                listing, BuyingOpportunity.TYPE_PRICE_LOW,
                new BigDecimal("100"), new BigDecimal("150"),
                new BigDecimal("15"), new BigDecimal("15"),
                scannedAt.plusSeconds(60), now.plusSeconds(60));

        verify(opportunities).recordPriceGuard(
                listing, BuyingOpportunity.TYPE_PRICE_LOW,
                new BigDecimal("100"), new BigDecimal("150"),
                new BigDecimal("15"), new BigDecimal("15"),
                scannedAt.plusSeconds(60));
        assertEquals(BuyingOpportunity.TYPE_PRICE_LOW, existing.getType());
        assertTrue(existing.isActive());
    }

    @Test
    void viewUsesChannelVisibilityAndInventoryLabel() {
        ListingAdjustment row = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);

        ListingAdjustmentService.ListingAdjustmentView view = service.view(row);

        assertEquals("ACTIVE", view.listingStatus());
        assertEquals("https://www.ebay.com/itm/123", view.listingUrl());
        assertEquals(listing.getInventoryItem().getId(), view.inventoryItemId());
        assertTrue(view.inventoryLabel().contains("10195-1"));
        assertEquals(0, new BigDecimal("200").compareTo(view.currentListingPrice()));
        assertEquals(0, new BigDecimal("150").compareTo(view.marketPrice()));
    }

    @Test
    void listActiveOmitsSoldInventory() {
        listing.getInventoryItem().applyStockAndQuantity(StockStatus.SOLD, 0);
        ListingAdjustment sold = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        ChannelListing live = listing(Platform.EBAY);
        live.markPublished("456", "https://www.ebay.com/itm/456", new BigDecimal("180"));
        live.getInventoryItem().applyStockAndQuantity(StockStatus.IN_STOCK, 1);
        ListingAdjustment open = ListingAdjustment.create(
                live, BuyingOpportunity.TYPE_PRICE_LOW,
                new BigDecimal("120"), new BigDecimal("150"), scannedAt);
        when(adjustments.findActiveWithListing()).thenReturn(List.of(sold, open));

        assertEquals(List.of(open), service.listActive());
    }

    @Test
    void resolveClearsActiveRow() {
        ListingAdjustment existing = ListingAdjustment.create(
                listing, BuyingOpportunity.TYPE_PRICE_HIGH,
                new BigDecimal("200"), new BigDecimal("150"), scannedAt);
        when(adjustments.findByIdWithListing(existing.getId())).thenReturn(Optional.of(existing));

        ListingAdjustmentService.ListingAdjustmentView view = service.resolve(existing.getId());

        assertTrue(existing.isResolved());
        assertEquals(listing.getInventoryItem().getId(), view.inventoryItemId());
        verify(adjustments).save(existing);
    }

    private static ChannelListing listing(Platform platform) {
        CatalogItem catalog = CatalogItem.create("10195-1");
        catalog.setName("Republic Dropship with AT-OT Walker");
        InventoryItem item = InventoryItem.create(catalog, "SKU-10195");
        item.setTitle("Republic Dropship");
        return ChannelListing.create(item, platform);
    }
}
