package com.thetimelessvault.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesLedgerMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsSetAndMinifigSalesNewestFirstAndComputesProfit() throws Exception {
        var data = mapper.readTree("""
                {
                  "set_sales_count": 2,
                  "minifig_sales_count": 1,
                  "set_sales": [
                    {
                      "set_number": "10292-1",
                      "name": "The Friends Apartments",
                      "theme": "Icons",
                      "currency": "USD",
                      "sale_price_total": 84.00,
                      "sale_price_unit": 78.00,
                      "sale_price_shipping": 5.00,
                      "sale_price_fees": 5.00,
                      "sale_quantity": 1,
                      "sale_condition": "new",
                      "sale_date": "2021-03-03",
                      "buy_price": 78.00
                    },
                    {
                      "set_number": "7190-1",
                      "name": "Millennium Falcon",
                      "theme": "Star Wars",
                      "currency": "USD",
                      "sale_price_total": 425.00,
                      "sale_price_unit": 415.00,
                      "sale_price_shipping": 10.00,
                      "sale_quantity": 1,
                      "sale_date": "2022-02-11",
                      "buy_price": 90.00
                    }
                  ],
                  "minifig_sales": [
                    {
                      "minifig_number": "sw0451",
                      "name": "Han Solo",
                      "currency": "USD",
                      "sale_price_total": 64.00,
                      "sale_price_unit": 58.00,
                      "sale_price_fees": 5.00,
                      "sale_quantity": 10,
                      "sale_date": "2021-03-03",
                      "buy_price": 38.00
                    }
                  ]
                }
                """);

        List<LedgerSale> sales = SalesLedgerMapper.from(data);

        assertEquals(3, sales.size());
        assertEquals("7190-1", sales.getFirst().itemNumber());
        assertEquals("SET", sales.getFirst().kind());
        assertEquals(0, new BigDecimal("325.00").compareTo(sales.getFirst().profit()));
        assertEquals("10292-1", sales.get(1).itemNumber());
        assertEquals(0, new BigDecimal("-5.00").compareTo(sales.get(1).profit()));
        assertEquals("sw0451", sales.get(2).itemNumber());
        assertEquals("MINIFIG", sales.get(2).kind());
        assertEquals(0, new BigDecimal("15.00").compareTo(sales.get(2).profit()));
    }

    @Test
    void profitIsUnitMinusFeesAndBuyPrice() {
        assertEquals(
                0,
                new BigDecimal("45.09").compareTo(SalesLedgerMapper.profit(
                        new BigDecimal("820.00"), new BigDecimal("15.00"), new BigDecimal("759.91")))
        );
    }
}
