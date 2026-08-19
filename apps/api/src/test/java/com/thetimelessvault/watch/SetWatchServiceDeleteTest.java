package com.thetimelessvault.watch;

import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.market.ScanLogRepository;
import com.thetimelessvault.settings.WatchDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetWatchServiceDeleteTest {

    @Mock SetWatchRepository watches;
    @Mock CatalogService catalogService;
    @Mock WatchDefaults watchDefaults;
    @Mock BuyingOpportunityService opportunities;
    @Mock ScanLogRepository scanLogs;

    SetWatchService service;
    CatalogItem catalog;
    SetWatch watch;

    @BeforeEach
    void setUp() {
        service = new SetWatchService(watches, catalogService, watchDefaults, opportunities, scanLogs);
        catalog = CatalogItem.create("75017-1");
        catalog.setName("Duel on Geonosis");
        watch = SetWatch.create(catalog);
        when(watches.findWithCatalogById(watch.getId())).thenReturn(Optional.of(watch));
    }

    @Test
    void deleteRemovesAlertsAndScanLogsBeforeTheWatch() {
        service.delete(watch.getId());

        InOrder order = inOrder(opportunities, scanLogs, watches);
        order.verify(opportunities).deleteNewListings(catalog.getId());
        order.verify(scanLogs).deleteByCatalogItemId(catalog.getId());
        order.verify(scanLogs).deleteBySetNumberIgnoreCase("75017-1");
        order.verify(watches).delete(watch);
    }
}
