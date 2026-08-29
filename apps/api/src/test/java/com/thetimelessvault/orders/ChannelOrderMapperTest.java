package com.thetimelessvault.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void mapsEbayOrdersWithMultipleLineItemsAndUnitPrice() throws Exception {
        var root = mapper.readTree("""
                {
                  "orders": [
                    {
                      "orderId": "12-multi",
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
                        },
                        {
                          "lineItemId": "li-2",
                          "sku": "TTV-10236-1-BBBB",
                          "legacyItemId": "444",
                          "title": "LEGO 10236 Eiffel Tower",
                          "quantity": 2,
                          "lineItemCost": { "value": "100.00", "currency": "USD" }
                        }
                      ],
                      "pricingSummary": { "deliveryCost": { "value": "12.50", "currency": "USD" } },
                      "totalMarketplaceFee": { "value": "35.99", "currency": "USD" }
                    }
                  ]
                }
                """);

        List<ChannelOrder> orders = ChannelOrderMapper.fromEbayOrders(root);

        assertEquals(2, orders.size());
        assertEquals("12-multi", orders.get(0).orderId());
        assertEquals("12-multi", orders.get(1).orderId());
        assertEquals("li-1", orders.get(0).lineId());
        assertEquals("li-2", orders.get(1).lineId());
        assertEquals(new BigDecimal("899.99"), orders.get(0).unitPrice());
        assertEquals(2, orders.get(1).quantity());
        assertEquals(new BigDecimal("50.00"), orders.get(1).unitPrice());
        assertEquals(new BigDecimal("12.50"), orders.get(0).shippingCost());
        assertEquals(new BigDecimal("35.99"), orders.get(0).platformFee());
        assertEquals(BigDecimal.ZERO, orders.get(1).shippingCost());
        assertEquals(BigDecimal.ZERO, orders.get(1).platformFee());
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
    void mapsBrickLinkOrdersWithMultipleItems() throws Exception {
        var order = mapper.readTree("""
                { "order_id": 88, "date_ordered": "2026-08-19T15:00:00.000Z", "status": "PAID",
                  "cost": { "shipping": "15.00", "subtotal": "870.00" } }
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
                  },
                  {
                    "inventory_id": 556,
                    "quantity": 1,
                    "remarks": "TTV-10236-1-BBBB",
                    "unit_price": "50.00",
                    "currency_code": "USD",
                    "item": { "no": "10236-1", "name": "Eiffel Tower" }
                  }
                ]]
                """);

        List<ChannelOrder> orders = ChannelOrderMapper.fromBrickLinkOrder(order, items);

        assertEquals(2, orders.size());
        assertEquals("88", orders.get(0).orderId());
        assertEquals("88", orders.get(1).orderId());
        assertEquals("TTV-75192-1-AAAA", orders.get(0).sku());
        assertEquals("TTV-10236-1-BBBB", orders.get(1).sku());
        assertEquals(new BigDecimal("15.00"), orders.get(0).shippingCost());
        assertEquals(BigDecimal.ZERO, orders.get(1).shippingCost());
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
        assertEquals("USPS", shippedLines.getFirst().shippingProvider());

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
    void mapsShopifyOrdersWithMultipleLineItems() throws Exception {
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
                          },
                          {
                            "id": "gid://shopify/LineItem/10",
                            "sku": "TTV-10236-1-BBBB",
                            "title": "LEGO 10236 Eiffel Tower",
                            "quantity": 1,
                            "originalUnitPriceSet": { "shopMoney": { "amount": "50.00", "currencyCode": "USD" } },
                            "product": { "id": "gid://shopify/Product/78" }
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        List<ChannelOrder> orders = ChannelOrderMapper.fromShopifyOrders(connection, "thetimelessvault.myshopify.com");

        assertEquals(2, orders.size());
        assertEquals("gid://shopify/Order/1001", orders.get(0).orderId());
        assertEquals("gid://shopify/Order/1001", orders.get(1).orderId());
        assertEquals("gid://shopify/LineItem/9", orders.get(0).lineId());
        assertEquals("gid://shopify/LineItem/10", orders.get(1).lineId());
        assertEquals(new BigDecimal("8.00"), orders.get(0).shippingCost());
        assertEquals(BigDecimal.ZERO, orders.get(1).shippingCost());
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

    @Test
    void skipsUnpaidBrickOwlOrders() throws Exception {
        var pending = mapper.readTree("""
                { "order_id": "1", "status_id": 0, "order_date": "1724788800" }
                """);
        var submitted = mapper.readTree("""
                { "order_id": "2", "status_id": 1, "order_date": "1724788800" }
                """);
        var items = mapper.readTree("""
                { "items": [{ "lot_id": "9", "name": "Falcon", "ordered_quantity": 1, "base_price": "100.00" }] }
                """);
        assertTrue(ChannelOrderMapper.fromBrickOwlOrder(pending, items).isEmpty());
        assertTrue(ChannelOrderMapper.fromBrickOwlOrder(submitted, items).isEmpty());
    }

    @Test
    void mapsBrickOwlStatusesAndShippingOnFirstLineOnly() throws Exception {
        var order = mapper.readTree("""
                {
                  "order_id": "88",
                  "status_id": 2,
                  "order_date": "1724788800",
                  "iso_currency": "USD",
                  "shipping_total": "12.50"
                }
                """);
        var items = mapper.readTree("""
                {
                  "items": [
                    {
                      "lot_id": "555",
                      "external_id": "TTV-75192-1-AAAA",
                      "name": "Millennium Falcon",
                      "ordered_quantity": 1,
                      "base_price": "820.00",
                      "ids": [{ "id_type": "set_number", "id": "75192-1" }]
                    },
                    {
                      "lot_id": "556",
                      "external_id": "TTV-10236-1-BBBB",
                      "name": "Eiffel Tower",
                      "ordered_quantity": 1,
                      "base_price": "50.00",
                      "ids": [{ "id_type": "set_number", "id": "10236-1" }]
                    }
                  ]
                }
                """);

        List<ChannelOrder> orders = ChannelOrderMapper.fromBrickOwlOrder(order, items);

        assertEquals(2, orders.size());
        ChannelOrder first = orders.getFirst();
        assertEquals(Platform.BRICKOWL, first.platform());
        assertEquals("88", first.orderId());
        assertEquals("555", first.listingExternalId());
        assertEquals("TTV-75192-1-AAAA", first.sku());
        assertEquals("75192-1", first.setNumber());
        assertEquals(new BigDecimal("820.00"), first.unitPrice());
        assertEquals(new BigDecimal("12.50"), first.shippingCost());
        assertEquals(BigDecimal.ZERO, first.platformFee());
        assertEquals(OrderStatus.OPEN, first.status());
        assertEquals(BigDecimal.ZERO, orders.get(1).shippingCost());
        assertEquals(OrderStatus.OPEN, orders.get(1).status());
    }

    @Test
    void mapsShippedBrickOwlTrackingAndCancelledStatus() throws Exception {
        var shipped = mapper.readTree("""
                {
                  "order_id": "89",
                  "status_id": 5,
                  "order_date": "1724788800",
                  "tracking_id": "9400222",
                  "shipping_method": "USPS Priority"
                }
                """);
        var trackedOpen = mapper.readTree("""
                {
                  "order_id": "91",
                  "status_id": 3,
                  "order_date": "1724788800",
                  "tracking_id": "1Z14V5340327789307"
                }
                """);
        var received = mapper.readTree("""
                { "order_id": "92", "status_id": 6, "order_date": "1724788800" }
                """);
        var cancelled = mapper.readTree("""
                { "order_id": "90", "status_id": 8, "order_date": "1724788800" }
                """);
        var items = mapper.readTree("""
                { "items": [{ "lot_id": "1", "name": "X", "ordered_quantity": 1, "base_price": "10.00" }] }
                """);

        ChannelOrder shippedLine = ChannelOrderMapper.fromBrickOwlOrder(shipped, items).getFirst();
        assertEquals(OrderStatus.SHIPPED, shippedLine.status());
        assertEquals("9400222", shippedLine.trackingNumber());
        assertEquals("USPS", shippedLine.shippingProvider());

        ChannelOrder tracked = ChannelOrderMapper.fromBrickOwlOrder(trackedOpen, items).getFirst();
        assertEquals(OrderStatus.SHIPPED, tracked.status());
        assertEquals("1Z14V5340327789307", tracked.trackingNumber());
        assertEquals("UPS", tracked.shippingProvider());

        assertEquals(OrderStatus.COMPLETED, ChannelOrderMapper.fromBrickOwlOrder(received, items).getFirst().status());
        assertEquals(OrderStatus.CANCELLED, ChannelOrderMapper.fromBrickOwlOrder(cancelled, items).getFirst().status());
    }
}
