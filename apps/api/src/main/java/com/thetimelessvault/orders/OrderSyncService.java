package com.thetimelessvault.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.shopify.ShopifyClient;
import com.thetimelessvault.inbound.PurchaseOrderDeliverySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OrderSyncService {

    static final String LAST_SYNC_KEY = "sales.last_sync_at";
    static final String LAST_EBAY_KEY = "sales.last_sync.ebay";
    static final String LAST_BRICKLINK_KEY = "sales.last_sync.bricklink";
    static final String LAST_SHOPIFY_KEY = "sales.last_sync.shopify";

    private static final Logger log = LoggerFactory.getLogger(OrderSyncService.class);
    private static final Duration LOOKBACK = Duration.ofDays(1);
    private static final Duration OVERLAP = Duration.ofHours(2);

    private final OrderService orderService;
    private final OrderRepository orders;
    private final AppSettingRepository settings;
    private final EbayClient ebayClient;
    private final BrickLinkClient brickLinkClient;
    private final ShopifyClient shopifyClient;
    private final ObjectMapper mapper;
    private final PurchaseOrderDeliverySyncService purchaseOrderDeliveries;
    private final OrderDeliverySyncService orderDeliveries;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OrderSyncService(
            OrderService orderService,
            OrderRepository orders,
            AppSettingRepository settings,
            EbayClient ebayClient,
            BrickLinkClient brickLinkClient,
            ShopifyClient shopifyClient,
            ObjectMapper mapper,
            PurchaseOrderDeliverySyncService purchaseOrderDeliveries,
            OrderDeliverySyncService orderDeliveries
    ) {
        this.orderService = orderService;
        this.orders = orders;
        this.settings = settings;
        this.ebayClient = ebayClient;
        this.brickLinkClient = brickLinkClient;
        this.shopifyClient = shopifyClient;
        this.mapper = mapper;
        this.purchaseOrderDeliveries = purchaseOrderDeliveries;
        this.orderDeliveries = orderDeliveries;
    }

    public Map<String, Object> sync() {
        if (!running.compareAndSet(false, true)) {
            return Map.of("status", "busy");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        int imported = 0;
        int ordersDelivered = 0;
        int purchaseOrdersDelivered = 0;
        try {
            imported += syncEbay();
            imported += syncBrickLink();
            imported += syncShopify();
            saveSetting(LAST_SYNC_KEY, Instant.now().toString());
            ordersDelivered = syncOrderDeliveries();
            purchaseOrdersDelivered = syncPurchaseOrderDeliveries();
        } finally {
            running.set(false);
        }
        result.put("imported", imported);
        result.put("ordersDelivered", ordersDelivered);
        result.put("purchaseOrdersDelivered", purchaseOrdersDelivered);
        return result;
    }

    int syncOrderDeliveries() {
        try {
            int delivered = orderDeliveries.syncDeliveredShipments();
            if (delivered > 0) {
                log.info("Sales order delivery sync marked {} open or shipped orders delivered", delivered);
            }
            return delivered;
        } catch (Exception e) {
            log.warn("Sales order delivery sync failed: {}", e.getMessage());
            return 0;
        }
    }

    int syncPurchaseOrderDeliveries() {
        try {
            int delivered = purchaseOrderDeliveries.syncDeliveredShipments();
            if (delivered > 0) {
                log.info("Purchase order delivery sync marked {} in-transit orders delivered", delivered);
            }
            return delivered;
        } catch (Exception e) {
            log.warn("Purchase order delivery sync failed: {}", e.getMessage());
            return 0;
        }
    }

    int syncEbay() {
        if (!ebayClient.sellReady()) {
            log.info("Skipping eBay order sync; sell API is not ready");
            return 0;
        }
        Instant since = since(LAST_EBAY_KEY);
        try {
            List<ChannelOrder> listed = ChannelOrderMapper.fromEbayOrders(ebayClient.getFulfillmentOrders(since));
            Set<String> seen = new LinkedHashSet<>();
            listed.forEach(line -> {
                if (line.orderId() != null) {
                    seen.add(line.orderId());
                }
            });
            int imported = importAll(enrichEbayTracking(listed));
            imported += refreshKnownEbayOrders(seen);
            log.info("eBay order sync imported {}", imported);
            saveSetting(LAST_EBAY_KEY, Instant.now().toString());
            return imported;
        } catch (Exception e) {
            log.warn("eBay order sync failed: {}", e.getMessage());
            return 0;
        }
    }

    int syncBrickLink() {
        if (!brickLinkClient.configured()) {
            log.info("Skipping BrickLink order sync; API is not configured");
            return 0;
        }
        Instant since = since(LAST_BRICKLINK_KEY);
        try {
            JsonNode unfiled = brickLinkClient.listReceivedOrders(false);
            JsonNode filed = brickLinkClient.listReceivedOrders(true);
            int unfiledCount = arraySize(unfiled);
            int filedCount = arraySize(filed);
            int imported = 0;
            imported += importBrickLinkOrders(unfiled, null);
            imported += importBrickLinkOrders(filed, since);
            log.info("BrickLink order sync: {} unfiled, {} filed since {}, imported {}",
                    unfiledCount, filedCount, since, imported);
            saveSetting(LAST_BRICKLINK_KEY, Instant.now().toString());
            return imported;
        } catch (Exception e) {
            log.warn("BrickLink order sync failed: {}", e.getMessage());
            return 0;
        }
    }

    int syncShopify() {
        if (!shopifyClient.configured()) {
            log.info("Skipping Shopify order sync; API is not configured");
            return 0;
        }
        shopifyClient.refreshOrdersAccess();
        Instant since = since(LAST_SHOPIFY_KEY);
        try {
            int imported = 0;
            String cursor = null;
            for (int page = 0; page < 20; page++) {
                JsonNode payload = shopifyClient.listOrdersSince(since, cursor);
                imported += importAll(ChannelOrderMapper.fromShopifyOrders(
                        payload.path("orders"), shopifyClient.shopHost()));
                JsonNode pageInfo = payload.path("orders").path("pageInfo");
                if (!pageInfo.path("hasNextPage").asBoolean(false)) {
                    break;
                }
                cursor = pageInfo.path("endCursor").asText(null);
                if (cursor == null || cursor.isBlank()) {
                    break;
                }
            }
            saveSetting(LAST_SHOPIFY_KEY, Instant.now().toString());
            return imported;
        } catch (Exception e) {
            log.warn("Shopify order sync failed: {}", e.getMessage());
            return 0;
        }
    }

    private int importBrickLinkOrders(JsonNode orders, Instant since) {
        int imported = 0;
        if (orders == null || orders.isNull() || orders.isMissingNode()) {
            return 0;
        }
        if (!orders.isArray()) {
            log.warn("BrickLink orders response was not a list: {}", trimJson(orders));
            return 0;
        }
        for (JsonNode order : orders) {
            Instant orderedAt = tryParseInstant(order.path("date_ordered").asText(null));
            Instant statusChanged = tryParseInstant(order.path("date_status_changed").asText(null));
            Instant newest = later(orderedAt, statusChanged);
            if (since != null && newest != null && newest.isBefore(since)) {
                continue;
            }
            String orderId = order.path("order_id").asText(null);
            if (orderId == null || orderId.isBlank()) {
                continue;
            }
            try {
                JsonNode detail = order;
                try {
                    JsonNode fetched = brickLinkClient.getOrder(orderId);
                    if (fetched != null && !fetched.isMissingNode() && !fetched.isNull()) {
                        detail = fetched;
                    }
                } catch (Exception e) {
                    log.info("BrickLink order {} detail failed: {}", orderId, e.getMessage());
                }
                JsonNode items = brickLinkClient.getOrderItems(orderId);
                List<ChannelOrder> lines = ChannelOrderMapper.fromBrickLinkOrder(detail, items);
                if (lines.isEmpty()) {
                    log.info("BrickLink order {} produced no order lines (status={}, items={})",
                            orderId,
                            order.path("status").asText(""),
                            trimJson(items));
                }
                imported += importAll(lines);
            } catch (Exception e) {
                log.warn("BrickLink order {} items failed: {}", orderId, e.getMessage());
            }
        }
        return imported;
    }

    private List<ChannelOrder> enrichEbayTracking(List<ChannelOrder> incoming) {
        Map<String, ChannelOrderMapper.Tracking> byOrder = new LinkedHashMap<>();
        List<ChannelOrder> enriched = new ArrayList<>();
        for (ChannelOrder line : incoming) {
            if (line.status() == OrderStatus.CANCELLED || line.orderId() == null) {
                enriched.add(line);
                continue;
            }
            if (line.trackingNumber() != null) {
                enriched.add(line);
                continue;
            }
            ChannelOrderMapper.Tracking shipment = byOrder.computeIfAbsent(line.orderId(), id -> {
                try {
                    return ChannelOrderMapper.fromEbayShippingFulfillments(ebayClient.getShippingFulfillments(id));
                } catch (Exception e) {
                    log.info("eBay shipping fulfillment {} failed: {}", id, e.getMessage());
                    return ChannelOrderMapper.Tracking.EMPTY;
                }
            });
            if (shipment.tracking() == null) {
                enriched.add(line);
                continue;
            }
            OrderStatus status = line.status() == OrderStatus.OPEN ? OrderStatus.SHIPPED : line.status();
            enriched.add(line.withFulfillment(status, shipment.tracking(), shipment.provider()));
        }
        return enriched;
    }

    private int refreshKnownEbayOrders(Set<String> alreadySeen) {
        Set<String> ids = new LinkedHashSet<>();
        orders.findByPlatformAndStatusSource(Platform.EBAY, OrderStatusSource.MIGRATION)
                .forEach(order -> ids.add(order.getExternalOrderId()));
        orders.findByPlatformAndStatusIn(Platform.EBAY, List.of(OrderStatus.OPEN, OrderStatus.SHIPPED))
                .forEach(order -> ids.add(order.getExternalOrderId()));
        ids.removeAll(alreadySeen);
        ids.remove(null);
        int imported = 0;
        for (String orderId : ids) {
            try {
                JsonNode detail = ebayClient.getFulfillmentOrder(orderId);
                if (detail == null || detail.isMissingNode() || detail.path("orderId").asText("").isBlank()) {
                    continue;
                }
                ObjectNode root = mapper.createObjectNode();
                root.putArray("orders").add(detail);
                imported += importAll(enrichEbayTracking(ChannelOrderMapper.fromEbayOrders(root)));
            } catch (Exception e) {
                log.info("eBay refresh {} failed: {}", orderId, e.getMessage());
            }
        }
        return imported;
    }

    private static int arraySize(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private static Instant later(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static String trimJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "null";
        }
        String text = node.toString();
        return text.length() <= 500 ? text : text.substring(0, 500) + "…";
    }

    private int importAll(List<ChannelOrder> incoming) {
        int imported = 0;
        for (ChannelOrder order : incoming) {
            if (orderService.importOrder(order)) {
                imported++;
            }
        }
        return imported;
    }

    private Instant since(String key) {
        return settings.findById(key)
                .map(AppSetting::getValue)
                .map(OrderSyncService::tryParseInstant)
                .map(at -> at.minus(OVERLAP))
                .orElse(Instant.now().minus(LOOKBACK));
    }

    private void saveSetting(String key, String value) {
        AppSetting setting = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        settings.save(setting);
    }

    private static Instant tryParseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            // BrickLink sometimes omits Z or uses +0000 / -0400 offsets.
        }
        try {
            return java.time.OffsetDateTime.parse(text).toInstant();
        } catch (Exception ignored) {
            // Try compact offsets next.
        }
        for (String pattern : List.of(
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ssXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        )) {
            try {
                return java.time.OffsetDateTime.parse(text, java.time.format.DateTimeFormatter.ofPattern(pattern))
                        .toInstant();
            } catch (Exception ignored) {
                // Try the next pattern.
            }
        }
        try {
            return java.time.LocalDateTime.parse(text).toInstant(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }
}
