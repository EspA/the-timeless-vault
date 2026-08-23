package com.thetimelessvault.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelOrderMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsPaidAndCancelledEbayOrders() throws Exception {
        var root = mapper.readTree("""
                {
                  "orders": [
                    {
                      "orderId": "12-345",
                      "creationDate": "2026-08-20T12:00:00.000Z",
                      "orderPaymentStatus": "PAID",
                      "orderFulfillmentStatus": "NOT_STARTED",
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

        List<ChannelOrder> orders = ChannelOrderMapper.fromEbayOrders(root);

        assertEquals(2, orders.size());
        ChannelOrder open = orders.getFirst();
        assertEquals(Platform.EBAY, open.platform());
        assertEquals("12-345", open.orderId());
        assertEquals("TTV-75192-1-AAAA", open.sku());
        assertEquals("333", open.listingExternalId());
        assertEquals("75192-1", open.setNumber());
        assertEquals(new BigDecimal("899.99"), open.unitPrice());
        assertEquals(new BigDecimal("12.50"), open.shippingCost());
        assertEquals(new BigDecimal("35.99"), open.platformFee());
        assertEquals(OrderStatus.OPEN, open.status());
        assertEquals(OrderStatus.CANCELLED, orders.get(1).status());
        assertEquals("12-999", orders.get(1).orderId());
    }

    @Test
    void mapsFulfilledEbayOrderToShipped() throws Exception {
        var root = mapper.readTree("""
                {
                  "orders": [
                    {
                      "orderId": "12-777",
                      "creationDate": "2026-08-20T12:00:00.000Z",
                      "orderPaymentStatus": "PAID",
                      "orderFulfillmentStatus": "FULFILLED",
                      "cancelStatus": { "cancelState": "NONE_REQUESTED" },
                      "fulfillments": [
                        { "shipmentTrackingNumber": "9400111", "shippingCarrierCode": "USPS" }
                      ],
                      "lineItems": [
                        {
                          "lineItemId": "li-7",
                          "sku": "TTV-75192-1-AAAA",
                          "title": "LEGO 75192",
                          "quantity": 1,
                          "lineItemCost": { "value": "100.00", "currency": "USD" }
                        }
                      ]
                    }
                  ]
                }
                """);

        List<ChannelOrder> orders = ChannelOrderMapper.fromEbayOrders(root);
        assertEquals(1, orders.size());
        assertEquals(OrderStatus.SHIPPED, orders.getFirst().status());
        assertEquals("9400111", orders.getFirst().trackingNumber());
        assertEquals("USPS", orders.getFirst().shippingProvider());
    }

    @Test
    void skipsUnpaidEbayOrders() throws Exception {
        var root = mapper.readTree("""
                {
                  "orders": [
                    {
                      "orderId": "12-pending",
                      "orderPaymentStatus": "PENDING",
                      "lineItems": [{ "lineItemId": "li-p", "title": "Pending", "quantity": 1 }]
                    }
                  ]
                }
                """);
        assertTrue(ChannelOrderMapper.fromEbayOrders(root).isEmpty());
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

        List<ChannelOrder> orders = ChannelOrderMapper.fromBrickLinkOrder(order, items);

        assertEquals(1, orders.size());
        ChannelOrder mapped = orders.getFirst();
        assertEquals(Platform.BRICKLINK, mapped.platform());
        assertEquals("88", mapped.orderId());
        assertEquals("555", mapped.listingExternalId());
        assertEquals("TTV-75192-1-AAAA", mapped.sku());
        assertEquals("75192-1", mapped.setNumber());
        assertEquals(new BigDecimal("15.00"), mapped.shippingCost());
        assertEquals(BigDecimal.ZERO, mapped.platformFee());
        assertEquals(OrderStatus.OPEN, mapped.status());
    }

    @Test
    void mapsShippedBrickLinkTrackingAndCancelledStatus() throws Exception {
        var shipped = mapper.readTree("""
                { "order_id": 89, "date_ordered": "2026-08-19T15:00:00.000Z", "status": "SHIPPED",
                  "shipping": { "tracking_no": "9400222", "method": "USPS Priority" },
                  "cost": { "shipping": "12.00" } }
                """);
        var cancelled = mapper.readTree("""
                { "order_id": 90, "date_ordered": "2026-08-19T15:00:00.000Z", "status": "CANCELLED",
                  "cost": { "shipping": "0.00" } }
                """);
        var items = mapper.readTree("""
                [[{ "inventory_id": 1, "quantity": 1, "unit_price": "10.00", "item": { "no": "1", "name": "X" } }]]
                """);

        List<ChannelOrder> shippedLines = ChannelOrderMapper.fromBrickLinkOrder(shipped, items);
        assertEquals(OrderStatus.SHIPPED, shippedLines.getFirst().status());
        assertEquals("9400222", shippedLines.getFirst().trackingNumber());
        assertNull(shippedLines.getFirst().shippingProvider());

        List<ChannelOrder> cancelledLines = ChannelOrderMapper.fromBrickLinkOrder(cancelled, items);
        assertEquals(OrderStatus.CANCELLED, cancelledLines.getFirst().status());
    }

    @Test
    void infersUpsFromBrickLinkOneZTrackingAndIgnoresMethod() throws Exception {
        var shipped = mapper.readTree("""
                { "order_id": 91, "date_ordered": "2026-08-19T15:00:00.000Z", "status": "SHIPPED",
                  "shipping": { "tracking_no": "1Z14V5340327789307", "method": "Request for Invoice" },
                  "cost": { "shipping": "12.00" } }
                """);
        var items = mapper.readTree("""
                [[{ "inventory_id": 1, "quantity": 1, "unit_price": "10.00", "item": { "no": "1", "name": "X" } }]]
                """);

        ChannelOrder mapped = ChannelOrderMapper.fromBrickLinkOrder(shipped, items).getFirst();
        assertEquals("1Z14V5340327789307", mapped.trackingNumber());
        assertEquals("UPS", mapped.shippingProvider());
    }

    @Test
    void mapsShopifyPaidCancelledAndDeliveredOrders() throws Exception {
        var connection = mapper.readTree("""
                {
                  "nodes": [
                    {
                      "id": "gid://shopify/Order/1001",
                      "name": "#1001",
                      "processedAt": "2026-08-18T10:00:00Z",
                      "cancelledAt": null,
                      "displayFinancialStatus": "PAID",
                      "displayFulfillmentStatus": "UNFULFILLED",
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
                    },
                    {
                      "id": "gid://shopify/Order/1003",
                      "processedAt": "2026-08-18T10:00:00Z",
                      "cancelledAt": null,
                      "displayFinancialStatus": "PAID",
                      "displayFulfillmentStatus": "FULFILLED",
                      "fulfillments": [
                        {
                          "displayStatus": "DELIVERED",
                          "deliveredAt": "2026-08-20T10:00:00Z",
                          "trackingInfo": [{ "number": "1Z123", "company": "UPS" }]
                        }
                      ],
                      "lineItems": { "nodes": [{ "id": "y", "title": "Delivered", "quantity": 1 }] }
                    }
                  ]
                }
                """);

        List<ChannelOrder> orders = ChannelOrderMapper.fromShopifyOrders(connection, "thetimelessvault.myshopify.com");

        assertEquals(3, orders.size());
        assertEquals(Platform.SHOPIFY, orders.getFirst().platform());
        assertEquals("gid://shopify/Order/1001", orders.getFirst().orderId());
        assertEquals("gid://shopify/Product/77", orders.getFirst().listingExternalId());
        assertTrue(orders.getFirst().orderUrl().contains("/orders/1001"));
        assertEquals(new BigDecimal("8.00"), orders.getFirst().shippingCost());
        assertEquals(OrderStatus.OPEN, orders.getFirst().status());
        assertEquals(OrderStatus.CANCELLED, orders.get(1).status());
        assertEquals(OrderStatus.COMPLETED, orders.get(2).status());
        assertEquals("1Z123", orders.get(2).trackingNumber());
        assertEquals("UPS", orders.get(2).shippingProvider());
    }

    @Test
    void skipsPendingShopifyOrders() throws Exception {
        var connection = mapper.readTree("""
                {
                  "nodes": [
                    {
                      "id": "gid://shopify/Order/9",
                      "displayFinancialStatus": "PENDING",
                      "lineItems": { "nodes": [{ "id": "z", "title": "Wait", "quantity": 1 }] }
                    }
                  ]
                }
                """);
        assertTrue(ChannelOrderMapper.fromShopifyOrders(connection, "thetimelessvault.myshopify.com").isEmpty());
    }
}
