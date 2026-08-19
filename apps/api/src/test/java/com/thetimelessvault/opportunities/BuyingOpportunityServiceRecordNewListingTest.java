package com.thetimelessvault.opportunities;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.market.MarketListing;
import com.thetimelessvault.market.MarketSnapshot;
import com.thetimelessvault.market.ScanTrigger;
import com.thetimelessvault.settings.AlertMailer;
import com.thetimelessvault.watch.SetWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyingOpportunityServiceRecordNewListingTest {

    @Mock BuyingOpportunityRepository opportunities;
    @Mock AlertMailer alertMailer;

    BuyingOpportunityService service;

    private CatalogItem catalog;
    private SetWatch watch;
    private MarketListing listing;
    private String dedupe;

    @BeforeEach
    void setUp() {
        service = new BuyingOpportunityService(opportunities, alertMailer);
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
        dedupe = "NEW:EBAY:" + catalog.getId() + ":v1|146877641523|0";
    }

    @Test
    void firstAutomaticSightingCreatesAnOpportunityEvenWhenAManualOneAlreadyExists() {
        when(opportunities.findByDedupeKeyAndScanTrigger(dedupe, ScanTrigger.AUTOMATIC)).thenReturn(Optional.empty());
        when(opportunities.save(any(BuyingOpportunity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(alertMailer.sendQuietly(any(), any())).thenReturn(false);

        service.recordNewListing(catalog, Platform.EBAY, listing, watch);

        ArgumentCaptor<BuyingOpportunity> saved = ArgumentCaptor.forClass(BuyingOpportunity.class);
        verify(opportunities).save(saved.capture());
        assertEquals(BuyingOpportunity.TYPE_BUYING_OPPORTUNITY, saved.getValue().getType());
        assertEquals(ScanTrigger.AUTOMATIC, saved.getValue().getScanTrigger());
        assertEquals(dedupe, saved.getValue().getDedupeKey());
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
}
