package com.thetimelessvault.watch;

import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.market.ScanLogRepository;
import com.thetimelessvault.settings.WatchDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetWatchServiceEnsureTest {

    @Mock SetWatchRepository watches;
    @Mock CatalogService catalogService;
    @Mock WatchDefaults watchDefaults;
    @Mock BuyingOpportunityService opportunities;
    @Mock ScanLogRepository scanLogs;

    SetWatchService service;
    CatalogItem catalog;

    @BeforeEach
    void setUp() {
        service = new SetWatchService(watches, catalogService, watchDefaults, opportunities, scanLogs);
        catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        catalog.setTheme("Star Wars");
    }

    @Test
    void createsADisabledWatchWhenNoneExists() {
        when(watches.findByCatalogItemId(catalog.getId())).thenReturn(Optional.empty());
        when(watches.findBySetNumberIgnoreCase("75192-1")).thenReturn(Optional.empty());
        when(watchDefaults.excludeWords()).thenReturn("-custom");
        ArgumentCaptor<SetWatch> saved = ArgumentCaptor.forClass(SetWatch.class);
        when(watches.saveAndFlush(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.ensureWatch(catalog, false));

        SetWatch watch = saved.getValue();
        assertFalse(watch.isEnabled());
        assertEquals("75192-1", watch.getSetNumber());
        assertEquals("-custom", watch.getEbayExcludeWords());
        assertTrue(watch.getEbaySearchQuery() != null && watch.getEbaySearchQuery().contains("75192"));
    }

    @Test
    void skipsWhenAWatchAlreadyExists() {
        SetWatch existing = SetWatch.create(catalog);
        existing.setEnabled(true);
        when(watches.findByCatalogItemId(catalog.getId())).thenReturn(Optional.of(existing));

        assertFalse(service.ensureWatch(catalog, false));
        verify(watches, never()).saveAndFlush(any());
    }
}
