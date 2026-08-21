package com.thetimelessvault.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.shopify.ShopifyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SalesSyncService {

    static final String LAST_SYNC_KEY = "sales.last_sync_at";
    static final String LAST_EBAY_KEY = "sales.last_sync.ebay";
    static final String LAST_BRICKLINK_KEY = "sales.last_sync.bricklink";
    static final String LAST_SHOPIFY_KEY = "sales.last_sync.shopify";

    private static final Logger log = LoggerFactory.getLogger(SalesSyncService.class);
    private static final Duration LOOKBACK = Duration.ofDays(1);
    private static final Duration OVERLAP = Duration.ofHours(2);

    private final SalesService salesService;
    private final AppSettingRepository settings;
    private final EbayClient ebayClient;
    private final BrickLinkClient brickLinkClient;
    private final ShopifyClient shopifyClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SalesSyncService(
            SalesService salesService,
            AppSettingRepository settings,
            EbayClient ebayClient,
            BrickLinkClient brickLinkClient,
            ShopifyClient shopifyClient
    ) {
        this.salesService = salesService;
        this.settings = settings;
        this.ebayClient = ebayClient;
        this.brickLinkClient = brickLinkClient;
        this.shopifyClient = shopifyClient;
    }

    public Map<String, Object> sync() {
        if (!running.compareAndSet(false, true)) {
            return Map.of("status", "busy");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        int imported = 0;
        try {
            imported += syncEbay();
            imported += syncBrickLink();
            imported += syncShopify();
            saveSetting(LAST_SYNC_KEY, Instant.now().toString());
        } finally {
            running.set(false);
        }
        result.put("imported", imported);
        return result;
    }

    int syncEbay() {
        if (!ebayClient.sellReady()) {
            log.info("Skipping eBay sales sync; sell API is not ready");
            return 0;
        }
        Instant since = since(LAST_EBAY_KEY);
        try {
            int imported = importAll(ChannelSaleMapper.fromEbayOrders(ebayClient.getFulfillmentOrders(since)));
            log.info("eBay sales sync imported {}", imported);
            saveSetting(LAST_EBAY_KEY, Instant.now().toString());
            return imported;
        } catch (Exception e) {
            log.warn("eBay sales sync failed: {}", e.getMessage());
            return 0;
        }
    }

    int syncBrickLink() {
        if (!brickLinkClient.configured()) {
            log.info("Skipping BrickLink sales sync; API is not configured");
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
            log.info("BrickLink sales sync: {} unfiled, {} filed since {}, imported {}",
                    unfiledCount, filedCount, since, imported);
            saveSetting(LAST_BRICKLINK_KEY, Instant.now().toString());
            return imported;
        } catch (Exception e) {
            log.warn("BrickLink sales sync failed: {}", e.getMessage());
            return 0;
        }
    }

    int syncShopify() {
        if (!shopifyClient.configured()) {
            log.info("Skipping Shopify sales sync; API is not configured");
            return 0;
        }
        shopifyClient.refreshOrdersAccess();
        Instant since = since(LAST_SHOPIFY_KEY);
        try {
            int imported = 0;
            String cursor = null;
            for (int page = 0; page < 20; page++) {
                JsonNode payload = shopifyClient.listOrdersSince(since, cursor);
                imported += importAll(ChannelSaleMapper.fromShopifyOrders(
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
            log.warn("Shopify sales sync failed: {}", e.getMessage());
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
                List<ChannelSale> lines = ChannelSaleMapper.fromBrickLinkOrder(detail, items);
                if (lines.isEmpty()) {
                    log.info("BrickLink order {} produced no sale lines (status={}, items={})",
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

    private int importAll(List<ChannelSale> incoming) {
        int imported = 0;
        for (ChannelSale sale : incoming) {
            if (salesService.importSale(sale)) {
                imported++;
            }
        }
        return imported;
    }

    private Instant since(String key) {
        return settings.findById(key)
                .map(AppSetting::getValue)
                .map(SalesSyncService::tryParseInstant)
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
