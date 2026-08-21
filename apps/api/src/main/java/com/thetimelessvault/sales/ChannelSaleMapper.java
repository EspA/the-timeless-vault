package com.thetimelessvault.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.Platform;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ChannelSaleMapper {

    private static final Set<String> BRICKLINK_SKIP = Set.of("CANCELLED", "PURGED", "N/A");
    private static final Set<String> EBAY_SKIP_PAYMENT = Set.of("FAILED", "FULLY_REFUNDED", "PENDING");
    private static final Set<String> SHOPIFY_SKIP_FINANCIAL = Set.of("VOIDED", "REFUNDED", "EXPIRED", "PENDING");

    private ChannelSaleMapper() {
    }

    public static List<ChannelSale> fromEbayOrders(JsonNode root) {
        List<ChannelSale> sales = new ArrayList<>();
        JsonNode orders = root == null ? null : root.path("orders");
        if (orders == null || !orders.isArray()) {
            return sales;
        }
        for (JsonNode order : orders) {
            if (isEbayCancelled(order) || isSkippedEbayPayment(order)) {
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
            boolean first = true;
            for (JsonNode line : lines) {
                String listingId = firstText(line, "legacyItemId", "listingId");
                if (listingId == null) {
                    listingId = text(line.path("listing"), "listingId");
                }
                String sku = firstText(line, "sku");
                String title = firstText(line, "title");
                sales.add(new ChannelSale(
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
                        first ? fee : BigDecimal.ZERO
                ));
                first = false;
            }
        }
        return sales;
    }

    public static List<ChannelSale> fromBrickLinkOrder(JsonNode order, JsonNode items) {
        List<ChannelSale> sales = new ArrayList<>();
        if (order == null || order.isMissingNode() || isSkippedBrickLink(order)) {
            return sales;
        }
        String orderId = firstText(order, "order_id");
        if (orderId == null) {
            orderId = order.path("order_id").asText(null);
        }
        Instant soldAt = instant(order, "date_ordered");
        String orderUrl = orderId == null ? null : "https://www.bricklink.com/orderDetail.asp?ID=" + orderId;
        BigDecimal shipping = brickLinkShipping(order);
        boolean first = true;
        for (JsonNode line : flattenBrickLinkItems(items)) {
            JsonNode item = line.path("item");
            String remarks = firstText(line, "remarks");
            sales.add(new ChannelSale(
                    Platform.BRICKLINK,
                    orderId,
                    firstNonNull(
                            firstText(line, "inventory_id"),
                            item.path("no").asText(null),
                            String.valueOf(sales.size())
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
                    BigDecimal.ZERO
            ));
            first = false;
        }
        return sales;
    }

    public static List<ChannelSale> fromShopifyOrders(JsonNode connection, String shopHost) {
        List<ChannelSale> sales = new ArrayList<>();
        JsonNode nodes = connection == null ? null : connection.path("nodes");
        if (nodes == null || !nodes.isArray()) {
            nodes = connection == null ? null : connection.path("edges");
        }
        if (nodes == null || !nodes.isArray()) {
            return sales;
        }
        String storeHandle = shopHandle(shopHost);
        for (JsonNode node : nodes) {
            JsonNode order = node.has("node") ? node.path("node") : node;
            if (hasText(order, "cancelledAt") || isSkippedShopifyFinancial(order)) {
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
            boolean first = true;
            for (JsonNode line : lines) {
                String sku = firstNonNull(
                        firstText(line, "sku"),
                        firstText(line.path("variant"), "sku")
                );
                String productId = firstText(line.path("product"), "id");
                sales.add(new ChannelSale(
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
                        BigDecimal.ZERO
                ));
                first = false;
            }
        }
        return sales;
    }

    static boolean isEbayCancelled(JsonNode order) {
        String state = firstText(order.path("cancelStatus"), "cancelState");
        return state != null && (state.equalsIgnoreCase("CANCELED") || state.equalsIgnoreCase("CANCELLED"));
    }

    static boolean isSkippedEbayPayment(JsonNode order) {
        String status = firstText(order, "orderPaymentStatus");
        return status != null && EBAY_SKIP_PAYMENT.contains(status.toUpperCase(Locale.ROOT));
    }

    static boolean isSkippedBrickLink(JsonNode order) {
        String status = firstText(order, "status");
        return status != null && BRICKLINK_SKIP.contains(status.toUpperCase(Locale.ROOT));
    }

    static boolean isSkippedShopifyFinancial(JsonNode order) {
        String status = firstText(order, "displayFinancialStatus");
        return status != null && SHOPIFY_SKIP_FINANCIAL.contains(status.toUpperCase(Locale.ROOT));
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
        String value = firstText(node, field);
        return value != null;
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
        String text = text(node, field);
        return parseInstant(text);
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
}
