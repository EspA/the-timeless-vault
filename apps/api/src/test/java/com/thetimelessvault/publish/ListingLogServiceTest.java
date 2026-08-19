package com.thetimelessvault.publish;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingLogServiceTest {

    @Mock ListingLogRepository logs;

    ListingLogService service;
    InventoryItem item;

    @BeforeEach
    void setUp() {
        service = new ListingLogService(logs);
        CatalogItem catalog = CatalogItem.create("79001-1");
        catalog.setName("Escape from Mirkwood Spiders");
        item = InventoryItem.create(catalog, "SKU-79001");
        item.setTitle("LEGO 79001 Escape from Mirkwood Spiders");
    }

    @Test
    void recordStoresSetSkuPlatformAndAction() {
        when(logs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.record(item, Platform.EBAY, ListingAction.CREATE, ListingLogStatus.SUCCESS, "https://ebay.example/itm/1");

        ArgumentCaptor<ListingLog> captor = ArgumentCaptor.forClass(ListingLog.class);
        verify(logs).save(captor.capture());
        ListingLog log = captor.getValue();
        assertEquals(item.getId(), log.getInventoryItemId());
        assertEquals("SKU-79001", log.getSku());
        assertEquals("79001-1", log.getSetNumber());
        assertEquals("LEGO 79001 Escape from Mirkwood Spiders", log.getItemTitle());
        assertEquals(Platform.EBAY, log.getPlatform());
        assertEquals(ListingAction.CREATE, log.getAction());
        assertEquals(ListingLogStatus.SUCCESS, log.getStatus());
        assertEquals("https://ebay.example/itm/1", log.getMessage());
    }

    @Test
    void listNewestFirstWithSizeCap() {
        when(logs.findAllByOrderByLoggedAtDesc(PageRequest.of(0, 10_000)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(0, 50_000);

        verify(logs).findAllByOrderByLoggedAtDesc(PageRequest.of(0, 10_000));
    }
}
