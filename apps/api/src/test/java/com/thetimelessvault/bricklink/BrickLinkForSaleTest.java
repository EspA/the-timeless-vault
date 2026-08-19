package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrickLinkForSaleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesCatalogItemId() {
        String html = "var _var_item = { idItem: 53788 , type: 'S' , itemno: '10143-1' };";
        assertEquals(53788L, BrickLinkForSale.parseItemId(html));
        assertEquals(157691L, BrickLinkForSale.parseItemId(
                "{\"result\":{\"typeList\":[{\"type\":\"S\",\"items\":[{\"idItem\":157691}]}]}}"
        ));
        assertNull(BrickLinkForSale.parseItemId("<html></html>"));
    }

    @Test
    void parsesUsdDisplayPrice() {
        assertEquals(new BigDecimal("3112.06"), BrickLinkForSale.parseUsdPrice("US $3,112.06"));
        assertEquals(new BigDecimal("620.01"), BrickLinkForSale.parseUsdPrice("US $620.01"));
        assertNull(BrickLinkForSale.parseUsdPrice("EUR 599.99"));
    }

    @Test
    void keepsOnlyNewSealedLots() throws Exception {
        var root = mapper.readTree("""
                {
                  "total_count": 2,
                  "list": [
                    {
                      "idInv": 1,
                      "codeNew": "U",
                      "codeComplete": "C",
                      "strDesc": "Used complete",
                      "mDisplaySalePrice": "US $100.00",
                      "n4Qty": 1,
                      "strStorename": "Used Shop"
                    },
                    {
                      "idInv": 508747687,
                      "codeNew": "N",
                      "codeComplete": "S",
                      "strDesc": "New sealed Death Star II",
                      "mDisplaySalePrice": "US $3,112.06",
                      "n4Qty": 1,
                      "strStorename": "Bricktraders",
                      "strSellerUsername": "thirteeneighty",
                      "strSellerCountryCode": "UK",
                      "strSellerCountryName": "United Kingdom",
                      "idInvImg": 1147724,
                      "typeInvImg": "J",
                      "strInvImgUrl": ""
                    }
                  ]
                }
                """);
        var lots = BrickLinkForSale.parseLots(root, "10143-1");
        assertEquals(1, lots.size());
        assertEquals(508747687L, lots.getFirst().inventoryId());
        assertEquals(new BigDecimal("3112.06"), lots.getFirst().price());
        assertEquals("New Sealed", lots.getFirst().condition());
        assertEquals("Bricktraders", lots.getFirst().seller());
        assertEquals("United Kingdom", lots.getFirst().sellerCountry());
        assertEquals("New sealed Death Star II", lots.getFirst().title());
        assertEquals("https://img.bricklink.com/myImg/Thumb/1147724.jpg", lots.getFirst().imageUrl());
        assertEquals("https://store.bricklink.com/thirteeneighty?itemID=508747687#/shop", lots.getFirst().url());
    }

    @Test
    void buildsStoreListingUrl() {
        assertEquals(
                "https://store.bricklink.com/kaden50?itemID=550740359#/shop",
                BrickLinkForSale.storeUrl("kaden50", 550740359L)
        );
    }

    @Test
    void usesCatalogImageWhenLotHasNoPhoto() throws Exception {
        var row = mapper.readTree("""
                { "idInvImg": 0, "strInvImgUrl": "", "typeInvImg": "" }
                """);
        assertEquals(
                "https://img.bricklink.com/ItemImage/SN/0/10143-1.png",
                BrickLinkForSale.imageUrl(row, "10143-1")
        );
    }
}
