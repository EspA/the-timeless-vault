package com.thetimelessvault.brickowl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.publish.PublishResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrickOwlPublisherTest {

    @Mock BrickOwlClient client;
    @InjectMocks BrickOwlPublisher publisher;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createLotThenUnlistsIt() throws Exception {
        CatalogItem catalog = CatalogItem.create("75192-1");
        InventoryItem item = InventoryItem.create(catalog, "TTV-75192-1-AAAA");
        item.setQuantity(1);
        item.setCondition(ItemCondition.NEW_SEALED);
        item.setBrickowlPrice(new BigDecimal("140.00"));
        when(client.resolveSetBoid("75192-1")).thenReturn("12345-38");
        when(client.createLot(item, "12345-38")).thenReturn("778899");
        when(client.getLot("778899")).thenReturn(mapper.readTree("""
                { "lot_id": "778899", "boid": "12345-38", "for_sale": 0 }
                """));

        PublishResult result = publisher.publish(item, List.of());

        verify(client).createLot(item, "12345-38");
        verify(client).setForSale("778899", false);
        assertEquals(Platform.BRICKOWL, publisher.platform());
        assertEquals("778899", result.externalId());
        assertEquals("UNLISTED", result.brickowlStatus());
        assertEquals("https://www.brickowl.com/boid/12345-38", result.liveUrl());
    }
}
