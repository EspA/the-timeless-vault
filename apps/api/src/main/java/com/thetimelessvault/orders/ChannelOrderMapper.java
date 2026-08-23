package com.thetimelessvault.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.Platform;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ChannelOrderMapper {

    private static final Set<String> BRICKLINK_SKIP = Set.of("NPB", "N/A");
    private static final Set<String> BRICKLINK_CANCELLED = Set.of(
            "CANCELLED", "CANCELED", "OCR", "NPX", "NRS", "NSS", "PURGED");
    private static final Set<String> BRICKLINK_SHIPPED = Set.of("SHIPPED");
    private static final Set<String> BRICKLINK_COMPLETED = Set.of("RECEIVED", "COMPLETED");
    private static final Set<String> EBAY_SKIP_PAYMENT = Set.of("FAILED", "PENDING");
    private static final Set<String> SHOPIFY_SKIP_FINANCIAL = Set.of("EXPIRED", "PENDING");

    private ChannelOrderMapper() {
    }

    public static List<ChannelOrder> fromEbayOrders(JsonNode root) {
        List<ChannelOrder> orders = new ArrayList<>();
        JsonNode nodes = root == null ? null : root.path("orders");
        if (nodes == null || !nodes.isArray()) {
            return orders;
        }
        for (JsonNode order : nodes) {
            if (isSkippedEbayPayment(order)) {
                continue;
            }
            String orderId = text(order, "orderId");
            Instant soldAt = instant(order, "creationDate");
            String orderUrl = orderId == null ? null : "https://www.ebay.com/sh/ord/details?orderid=" + orderId;
            JsonNode lines = order.path("lineItems");
            if (!lines.isArray()) {
                continue;
            }
            BigDecimal shipping = money(order.path("pricingSummary").path("deliveryCost"));
            BigDecimal fee = money(order.path("totalMarketplaceFee"));
            OrderStatus status = ebayStatus(order);
            Tracking shipment = ebayEmbeddedTracking(order);
            if (shipment.tracking() != null && status == OrderStatus.OPEN) {
                status = OrderStatus.SHIPPED;
            }
            boolean first = true;
            for (JsonNode line : lines) {
                String listingId = firstText(line, "legacyItemId", "listingId");
                if (listingId == null) {
                    listingId = text(line.path("listing"), "listingId");
                }
                String sku = firstText(line, "sku");
                String title = firstText(line, "title");
                orders.add(new ChannelOrder(
                        Platform.EBAY,
                        orderId,
                        firstText(line, "lineItemId"),
                        sku,
                        listingId,
                        title,
                        SetNumberParser.firstNonBlank(SetNumberParser.fromSku(sku), SetNumberParser.fromTitle(title)),
                        line.path("quantity").asInt(1),
                        money(line.path("lineItemCost"), line.path("total")),
                        currency(line.path("lineItemCost"), line.path("total")),
                        soldAt,
                        orderUrl,
                        first ? shipping : BigDecimal.ZERO,
                        first ? fee : BigDecimal.ZERO,
                        status,
                        shipment.tracking(),
                        shipment.provider()
                ));
                first = false;
            }
        }
        return orders;
    }

    public static List<ChannelOrder> fromBrickLinkOrder(JsonNode order, JsonNode items) {
        List<ChannelOrder> orders = new ArrayList<>();
        if (order == null || order.isMissingNode() || isSkippedBrickLink(order)) {
            return orders;
        }
        String orderId = firstText(order, "order_id");
        if (orderId == null) {
            orderId = order.path("order_id").asText(null);
        }
        Instant soldAt = instant(order, "date_ordered");
        String orderUrl = orderId == null ? null : "https://www.bricklink.com/orderDetail.asp?ID=" + orderId;
        BigDecimal shipping = brickLinkShipping(order);
        OrderStatus status = brickLinkStatus(order);
        Tracking shipment = brickLinkTracking(order);
        if (shipment.tracking() != null && status == OrderStatus.OPEN) {
            status = OrderStatus.SHIPPED;
        }
        boolean first = true;
        for (JsonNode line : flattenBrickLinkItems(items)) {
            JsonNode item = line.path("item");
            String remarks = firstText(line, "remarks");
            orders.add(new ChannelOrder(
                    Platform.BRICKLINK,
                    orderId,
                    firstNonNull(
                            firstText(line, "inventory_id"),
                            item.path("no").asText(null),
                            String.valueOf(orders.size())
                    ),
                    remarks,
                    firstText(line, "inventory_id"),
                    firstNonNull(firstText(item, "name"), firstText(line, "description")),
                    firstNonNull(firstText(item, "no"), SetNumberParser.fromSku(remarks)),
                    line.path("quantity").asInt(1),
                    decimal(line, "unit_price_final", "unit_price"),
                    firstNonNull(firstText(line, "currency_code"), "USD"),
                    soldAt,
                    orderUrl,
                    first ? shipping : BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    status,
                    shipment.tracking(),
                    shipment.provider()
            ));
            first = false;
        }
        return orders;
    }

    public static List<ChannelOrder> fromShopifyOrders(JsonNode connection, String shopHost) {
        List<ChannelOrder> orders = new ArrayList<>();
        JsonNode nodes = connection == null ? null : connection.path("nodes");
        if (nodes == null || !nodes.isArray()) {
            nodes = connection == null ? null : connection.path("edges");
        }
        if (nodes == null || !nodes.isArray()) {
            return orders;
        }
        String storeHandle = shopHandle(shopHost);
        for (JsonNode node : nodes) {
            JsonNode order = node.has("node") ? node.path("node") : node;
            if (isSkippedShopifyFinancial(order)) {
                continue;
            }
            String gid = firstText(order, "id");
            String numericId = shopifyNumericId(gid);
            Instant soldAt = firstInstant(order, "processedAt", "createdAt");
            String orderUrl = numericId == null || storeHandle == null
                    ? null
                    : "https://admin.shopify.com/store/" + storeHandle + "/orders/" + numericId;
            JsonNode lines = order.path("lineItems").path("nodes");
            if (!lines.isArray()) {
                continue;
            }
            BigDecimal shipping = firstPositive(
                    shopifyMoney(order.path("totalShippingPriceSet").path("shopMoney")),
                    shopifyMoney(order.path("currentShippingPriceSet").path("shopMoney"))
            );
            Tracking shipment = shopifyTracking(order);
            OrderStatus status = shopifyStatus(order, shipment);
            boolean first = true;
            for (JsonNode line : lines) {
                String sku = firstNonNull(
                        firstText(line, "sku"),
                        firstText(line.path("variant"), "sku")
                );
                String productId = firstText(line.path("product"), "id");
                orders.add(new ChannelOrder(
                        Platform.SHOPIFY,
                        firstNonNull(gid, firstText(order, "name")),
                        firstText(line, "id"),
                        sku,
                        productId,
                        firstText(line, "title"),
                        SetNumberParser.firstNonBlank(SetNumberParser.fromSku(sku), SetNumberParser.fromTitle(firstText(line, "title"))),
                        line.path("quantity").asInt(1),
                        shopifyMoney(line.path("originalUnitPriceSet").path("shopMoney")),
                        firstNonNull(
                                firstText(line.path("originalUnitPriceSet").path("shopMoney"), "currencyCode"),
                                "USD"
                        ),
                        soldAt,
                        orderUrl,
                        first ? shipping : BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        status,
                        shipment.tracking(),
                        shipment.provider()
                ));
                first = false;
            }
        }
        return orders;
    }

    static OrderStatus ebayStatus(JsonNode order) {
        if (isEbayCancelled(order)) {
            return OrderStatus.CANCELLED;
        }
        String payment = firstText(order, "orderPaymentStatus");
        if (payment != null && payment.equalsIgnoreCase("FULLY_REFUNDED")) {
            return OrderStatus.CANCELLED;
        }
        String fulfillment = firstText(order, "orderFulfillmentStatus");
        if (fulfillment != null && fulfillment.equalsIgnoreCase("FULFILLED")) {
            return OrderStatus.SHIPPED;
        }
        return OrderStatus.OPEN;
    }

    static OrderStatus brickLinkStatus(JsonNode order) {
        String status = firstText(order, "status");
        if (status == null) {
            return OrderStatus.OPEN;
        }
        String upper = status.toUpperCase(Locale.ROOT);
        if (BRICKLINK_CANCELLED.contains(upper)) {
            return OrderStatus.CANCELLED;
        }
        if (BRICKLINK_COMPLETED.contains(upper)) {
            return OrderStatus.COMPLETED;
        }
        if (BRICKLINK_SHIPPED.contains(upper)) {
            return OrderStatus.SHIPPED;
        }
        return OrderStatus.OPEN;
    }

    static OrderStatus shopifyStatus(JsonNode order, Tracking shipment) {
        if (hasText(order, "cancelledAt")) {
            return OrderStatus.CANCELLED;
        }
        String financial = firstText(order, "displayFinancialStatus");
        if (financial != null) {
            String upper = financial.toUpperCase(Locale.ROOT);
            if (upper.equals("VOIDED") || upper.equals("REFUNDED")) {
                return OrderStatus.CANCELLED;
            }
        }
        if (shopifyDelivered(order) || (shipment != null && shipment.delivered())) {
            return OrderStatus.COMPLETED;
        }
        String fulfillment = firstText(order, "displayFulfillmentStatus");
        if (fulfillment != null && fulfillment.equalsIgnoreCase("FULFILLED")) {
            return OrderStatus.SHIPPED;
        }
        if (shipment != null && shipment.tracking() != null) {
            return OrderStatus.SHIPPED;
        }
        return OrderStatus.OPEN;
    }

    static boolean isEbayCancelled(JsonNode order) {
        String state = firstText(order.path("cancelStatus"), "cancelState");
        return state != null && (state.equalsIgnoreCase("CANCELED") || state.equalsIgnoreCase("CANCELLED"));
    }

    static boolean isSkippedEbayPayment(JsonNode order) {
        if (isEbayCancelled(order)) {
            return false;
        }
        String status = firstText(order, "orderPaymentStatus");
        return status != null && EBAY_SKIP_PAYMENT.contains(status.toUpperCase(Locale.ROOT));
    }

    static boolean isSkippedBrickLink(JsonNode order) {
        String status = firstText(order, "status");
        return status != null && BRICKLINK_SKIP.contains(status.toUpperCase(Locale.ROOT));
    }

    static boolean isSkippedShopifyFinancial(JsonNode order) {
        if (hasText(order, "cancelledAt")) {
            return false;
        }
        String status = firstText(order, "displayFinancialStatus");
        return status != null && SHOPIFY_SKIP_FINANCIAL.contains(status.toUpperCase(Locale.ROOT));
    }

    static Tracking ebayEmbeddedTracking(JsonNode order) {
        String tracking = firstText(order, "shipmentTrackingNumber", "trackingNumber");
        String provider = firstText(order, "shippingCarrierCode", "shippingCarrier");
        JsonNode fulfillments = order.path("fulfillments");
        if (fulfillments.isArray()) {
            for (JsonNode fulfillment : fulfillments) {
                if (tracking == null) {
                    tracking = firstText(fulfillment, "shipmentTrackingNumber", "trackingNumber");
                }
                if (provider == null) {
                    provider = firstText(fulfillment, "shippingCarrierCode", "shippingCarrier");
                }
            }
        }
        return new Tracking(tracking, provider, false);
    }

    static Tracking fromEbayShippingFulfillments(JsonNode root) {
        if (root == null) {
            return Tracking.EMPTY;
        }
        JsonNode fulfillments = root.path("fulfillments");
        if (!fulfillments.isArray()) {
            fulfillments = root.path("shippingFulfillments");
        }
        String tracking = null;
        String provider = null;
        if (fulfillments.isArray()) {
            for (JsonNode fulfillment : fulfillments) {
                if (tracking == null) {
                    tracking = firstText(fulfillment, "shipmentTrackingNumber", "trackingNumber");
                }
                if (provider == null) {
                    provider = firstText(fulfillment, "shippingCarrierCode", "shippingCarrier");
                }
            }
        }
        if (tracking == null) {
            tracking = firstText(root, "shipmentTrackingNumber", "trackingNumber");
        }
        if (provider == null) {
            provider = firstText(root, "shippingCarrierCode", "shippingCarrier");
        }
        return new Tracking(tracking, provider, false);
    }

    static Tracking brickLinkTracking(JsonNode order) {
        JsonNode shipping = order == null ? null : order.path("shipping");
        return new Tracking(
                firstText(shipping, "tracking_no", "tracking_number"),
                null,
                false
        );
    }

    static Tracking shopifyTracking(JsonNode order) {
        JsonNode fulfillments = order.path("fulfillments");
        if (fulfillments.has("nodes")) {
            fulfillments = fulfillments.path("nodes");
        }
        if (!fulfillments.isArray()) {
            return Tracking.EMPTY;
        }
        String tracking = null;
        String provider = null;
        boolean delivered = false;
        for (JsonNode fulfillment : fulfillments) {
            String display = firstText(fulfillment, "displayStatus", "status");
            if (display != null && display.equalsIgnoreCase("DELIVERED")) {
                delivered = true;
            }
            if (hasText(fulfillment, "deliveredAt")) {
                delivered = true;
            }
            JsonNode info = fulfillment.path("trackingInfo");
            if (info.has("nodes")) {
                info = info.path("nodes");
            }
            if (!info.isArray()) {
                continue;
            }
            for (JsonNode track : info) {
                if (tracking == null) {
                    tracking = firstText(track, "number");
                }
                if (provider == null) {
                    provider = firstText(track, "company");
                }
            }
        }
        return new Tracking(tracking, provider, delivered);
    }

    static boolean shopifyDelivered(JsonNode order) {
        String fulfillment = firstText(order, "displayFulfillmentStatus");
        return fulfillment != null && fulfillment.equalsIgnoreCase("DELIVERED");
    }

    static List<JsonNode> flattenBrickLinkItems(JsonNode items) {
        List<JsonNode> lines = new ArrayList<>();
        if (items == null || items.isMissingNode() || items.isNull()) {
            return lines;
        }
        if (items.isArray()) {
            for (JsonNode entry : items) {
                if (entry.isArray()) {
                    entry.forEach(lines::add);
                } else if (entry.isObject()) {
                    lines.add(entry);
                }
            }
        }
        return lines;
    }

    private static boolean hasText(JsonNode node, String field) {
        return firstText(node, field) != null;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstNonNull(String... values) {
        return SetNumberParser.firstNonBlank(values);
    }

    private static Instant instant(JsonNode node, String field) {
        return parseInstant(text(node, field));
    }

    private static Instant firstInstant(JsonNode node, String... fields) {
        for (String field : fields) {
            Instant value = instant(node, field);
            if (value != null) {
                return value;
            }
        }
        return Instant.now();
    }

    private static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(text.replace(' ', 'T') + (text.endsWith("Z") ? "" : "Z"));
            } catch (DateTimeParseException e) {
                return Instant.now();
            }
        }
    }

    private static BigDecimal money(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            BigDecimal value = decimal(node, "value", "amount");
            if (value != null) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private static String currency(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = firstText(node, "currency", "currencyCode");
            if (value != null) {
                return value;
            }
        }
        return "USD";
    }

    private static BigDecimal shopifyMoney(JsonNode money) {
        return decimal(money, "amount");
    }

    private static BigDecimal brickLinkShipping(JsonNode order) {
        JsonNode cost = order == null ? null : order.path("cost");
        BigDecimal shipping = decimal(cost, "shipping");
        if (shipping.signum() > 0) {
            return shipping;
        }
        return decimal(order == null ? null : order.path("disp_cost"), "shipping");
    }

    private static BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        for (BigDecimal value : values) {
            if (value != null && value.signum() > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal decimal(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return BigDecimal.ZERO;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || value.asText().isBlank()) {
                continue;
            }
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException ignored) {
                // Try the next field.
            }
        }
        return BigDecimal.ZERO;
    }

    static String shopifyNumericId(String gid) {
        if (gid == null || gid.isBlank()) {
            return null;
        }
        int slash = gid.lastIndexOf('/');
        return slash >= 0 ? gid.substring(slash + 1) : gid;
    }

    static String shopHandle(String shopHost) {
        if (shopHost == null || shopHost.isBlank()) {
            return null;
        }
        String host = shopHost.trim().toLowerCase(Locale.ROOT);
        if (host.endsWith(".myshopify.com")) {
            return host.substring(0, host.length() - ".myshopify.com".length());
        }
        return host;
    }

    record Tracking(String tracking, String provider, boolean delivered) {
        static final Tracking EMPTY = new Tracking(null, null, false);
    }
}
