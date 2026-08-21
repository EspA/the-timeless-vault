package com.thetimelessvault.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelSaleMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsPaidEbayOrderAndSkipsCancelled() throws Exception {
        var root = mapper.readTree("""
                {
                  "orders": [
                    {
                      "orderId": "12-345",
                      "creationDate": "2026-08-20T12:00:00.000Z",
                      "orderPaymentStatus": "PAID",
                      "cancelStatus": { "cancelState": "NONE_REQUESTED" },
                      "lineItems": [
                        {
                          "lineItemId": "li-1",
                          "sku": "TTV-75192-1-AAAA",
                          "legacyItemId": "333",
                          "title": "LEGO 75192 Millennium Falcon",
                          "quantity": 1,
                          "lineItemCost": { "value": "899.99", "currency": "USD" }
                        }
                      ],
                      "pricingSummary": { "deliveryCost": { "value": "12.50", "currency": "USD" } },
                      "totalMarketplaceFee": { "value": "35.99", "currency": "USD" }
                    },
                    {
                      "orderId": "12-999",
                      "creationDate": "2026-08-20T12:00:00.000Z",
                      "orderPaymentStatus": "PAID",
                      "cancelStatus": { "cancelState": "CANCELED" },
                      "lineItems": [
                        {
                          "lineItemId": "li-2",
                          "sku": "TTV-75000-1-BBBB",
                          "title": "Cancelled",
                          "quantity": 1,
                          "lineItemCost": { "value": "10.00", "currency": "USD" }
                        }
                      ]
                    }
                  ]
                }
                """);

        List<ChannelSale> sales = ChannelSaleMapper.fromEbayOrders(root);

        assertEquals(1, sales.size());
        ChannelSale sale = sales.getFirst();
        assertEquals(Platform.EBAY, sale.platform());
        assertEquals("12-345", sale.orderId());
        assertEquals("TTV-75192-1-AAAA", sale.sku());
        assertEquals("333", sale.listingExternalId());
        assertEquals("75192-1", sale.setNumber());
        assertEquals(new BigDecimal("899.99"), sale.unitPrice());
        assertEquals(new BigDecimal("12.50"), sale.shippingCost());
        assertEquals(new BigDecimal("35.99"), sale.platformFee());
    }

    @Test
    void mapsBrickLinkOrderItemsAndUsesRemarksAsSku() throws Exception {
        var order = mapper.readTree("""
                { "order_id": 88, "date_ordered": "2026-08-19T15:00:00.000Z", "status": "PAID",
                  "cost": { "shipping": "15.00", "subtotal": "820.00" } }
                """);
        var items = mapper.readTree("""
                [[
                  {
                    "inventory_id": 555,
                    "quantity": 1,
                    "remarks": "TTV-75192-1-AAAA",
                    "unit_price": "820.00",
                    "currency_code": "USD",
                    "item": { "no": "75192-1", "name": "Millennium Falcon" }
                  }
                ]]
                """);

        List<ChannelSale> sales = ChannelSaleMapper.fromBrickLinkOrder(order, items);

        assertEquals(1, sales.size());
        ChannelSale sale = sales.getFirst();
        assertEquals(Platform.BRICKLINK, sale.platform());
        assertEquals("88", sale.orderId());
        assertEquals("555", sale.listingExternalId());
        assertEquals("TTV-75192-1-AAAA", sale.sku());
        assertEquals("75192-1", sale.setNumber());
        assertEquals(new BigDecimal("15.00"), sale.shippingCost());
        assertEquals(BigDecimal.ZERO, sale.platformFee());
    }

    @Test
    void mapsShopifyPaidOrderAndSkipsCancelled() throws Exception {
        var connection = mapper.readTree("""
                {
                  "nodes": [
                    {
                      "id": "gid://shopify/Order/1001",
                      "name": "#1001",
                      "processedAt": "2026-08-18T10:00:00Z",
                      "cancelledAt": null,
                      "displayFinancialStatus": "PAID",
                      "totalShippingPriceSet": { "shopMoney": { "amount": "8.00", "currencyCode": "USD" } },
                      "lineItems": {
                        "nodes": [
                          {
                            "id": "gid://shopify/LineItem/9",
                            "sku": "TTV-75192-1-AAAA",
                            "title": "LEGO 75192 Millennium Falcon",
                            "quantity": 1,
                            "originalUnitPriceSet": { "shopMoney": { "amount": "910.00", "currencyCode": "USD" } },
                            "product": { "id": "gid://shopify/Product/77" }
                          }
                        ]
                      }
                    },
                    {
                      "id": "gid://shopify/Order/1002",
                      "cancelledAt": "2026-08-18T11:00:00Z",
                      "displayFinancialStatus": "PAID",
                      "lineItems": { "nodes": [{ "id": "x", "title": "Nope", "quantity": 1 }] }
                    }
                  ]
                }
                """);

        List<ChannelSale> sales = ChannelSaleMapper.fromShopifyOrders(connection, "thetimelessvault.myshopify.com");

        assertEquals(1, sales.size());
        assertEquals(Platform.SHOPIFY, sales.getFirst().platform());
        assertEquals("gid://shopify/Order/1001", sales.getFirst().orderId());
        assertEquals("gid://shopify/Product/77", sales.getFirst().listingExternalId());
        assertTrue(sales.getFirst().orderUrl().contains("/orders/1001"));
        assertEquals(new BigDecimal("8.00"), sales.getFirst().shippingCost());
        assertEquals(BigDecimal.ZERO, sales.getFirst().platformFee());
    }
}
