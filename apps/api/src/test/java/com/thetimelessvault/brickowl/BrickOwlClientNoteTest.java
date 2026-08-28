package com.thetimelessvault.brickowl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.InventoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrickOwlClientNoteTest {

    private BrickOwlClient client;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getBrickowl().setApiKey("test-key");
        client = new BrickOwlClient(properties, new ObjectMapper());
    }

    @Test
    void updateLotSendsShortDescriptionAsPublicNote() {
        InventoryItem item = sealedFalcon("NISB, no price sticker");

        MultiValueMap<String, String> body = client.updateLotRequest("778899", item);

        assertEquals("778899", body.getFirst("lot_id"));
        assertEquals("1", body.getFirst("absolute_quantity"));
        assertEquals("140.00", body.getFirst("price"));
        assertEquals("news", body.getFirst("condition"));
        assertEquals("TTV-75192-1-AAAA", body.getFirst("update_external_id_1"));
        assertEquals("NISB, no price sticker", body.getFirst("public_note"));
    }

    @Test
    void updateLotStripsHtmlFromThePublicNote() {
        InventoryItem item = sealedFalcon("<b>NISB</b>, no price sticker");

        MultiValueMap<String, String> body = client.updateLotRequest("778899", item);

        assertEquals("NISB, no price sticker", body.getFirst("public_note"));
    }

    @Test
    void blankShortDescriptionClearsThePublicNote() {
        InventoryItem item = sealedFalcon("  ");

        MultiValueMap<String, String> body = client.updateLotRequest("778899", item);

        assertEquals("", body.getFirst("public_note"));
    }

    @Test
    void unlistAfterCreateIncludesThePublicNote() {
        InventoryItem item = sealedFalcon("NISB, no price sticker");

        MultiValueMap<String, String> body = client.setForSaleRequest("778899", false, item);

        assertEquals("0", body.getFirst("for_sale"));
        assertEquals("NISB, no price sticker", body.getFirst("public_note"));
    }

    @Test
    void visibilityToggleDoesNotTouchThePublicNote() {
        MultiValueMap<String, String> body = client.setForSaleRequest("778899", true, null);

        assertEquals("1", body.getFirst("for_sale"));
        assertNull(body.getFirst("public_note"));
    }

    private static InventoryItem sealedFalcon(String shortDescription) {
        CatalogItem catalog = CatalogItem.create("75192-1");
        InventoryItem item = InventoryItem.create(catalog, "TTV-75192-1-AAAA");
        item.setQuantity(1);
        item.setCondition(ItemCondition.NEW_SEALED);
        item.setBrickowlPrice(new BigDecimal("140.00"));
        item.setShortDescription(shortDescription);
        return item;
    }
}
