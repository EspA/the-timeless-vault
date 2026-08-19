package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrickLinkClientForSaleTest {

    private MockRestServiceServer server;
    private BrickLinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        client = new BrickLinkClient(new AppProperties(), new ObjectMapper(), rest, rest);
    }

    @Test
    void loadsLotsFromSearchAjaxThenCatalogIfs() {
        server.expect(once(), requestTo(containsString("searchproduct.ajax")))
                .andRespond(withSuccess(
                        "{\"result\":{\"typeList\":[{\"type\":\"S\",\"items\":[{\"idItem\":157691}]}]}}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo(containsString("catalogifs.ajax")))
                .andRespond(withSuccess("""
                        {
                          "returnCode": 0,
                          "total_count": 1,
                          "list": [
                            {
                              "idInv": 42,
                              "codeNew": "N",
                              "codeComplete": "S",
                              "strDesc": "New sealed",
                              "mDisplaySalePrice": "US $100.00",
                              "n4Qty": 1,
                              "strStorename": "Vault",
                              "strSellerUsername": "vault",
                              "strSellerCountryName": "USA"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var lots = client.forSaleNewSealedShipsToUsa("75192-1");

        assertEquals(1, lots.size());
        assertEquals(42L, lots.getFirst().inventoryId());
        server.verify();
    }

    @Test
    void failsWhenCatalogLookupIsBlocked() {
        server.expect(manyTimes(), requestTo(containsString("bricklink.com")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        ApiException error = assertThrows(ApiException.class, () -> client.forSaleNewSealedShipsToUsa("75192-1"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        assertEquals("BrickLink blocked the catalog lookup from this server.", error.getMessage());
    }
}
