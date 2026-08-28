package com.thetimelessvault.brickowl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.ChannelPrice;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.settings.ApiCallStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BrickOwlClient {

    public static final String API_CALLS = "BRICKOWL";
    private static final Logger log = LoggerFactory.getLogger(BrickOwlClient.class);
    private static final int ORDER_PAGE_SIZE = 500;
    private static final int BULK_ORDER_CHUNK = 25;

    private final AppProperties.Brickowl config;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private ApiCallStatsService apiCalls;

    @Autowired
    public BrickOwlClient(AppProperties properties, ObjectMapper mapper) {
        this(properties, mapper, RestClient.builder().build());
    }

    BrickOwlClient(AppProperties properties, ObjectMapper mapper, RestClient restClient) {
        this.config = properties.getBrickowl();
        this.mapper = mapper;
        this.restClient = restClient;
    }

    @Autowired(required = false)
    public void setApiCalls(ApiCallStatsService apiCalls) {
        this.apiCalls = apiCalls;
    }

    public boolean configured() {
        return config.configured();
    }

    public boolean healthy() {
        if (!configured()) {
            return false;
        }
        try {
            userDetails();
            return true;
        } catch (RuntimeException e) {
            log.warn("Brick Owl health check failed: {}", e.getMessage());
            return false;
        }
    }

    public JsonNode userDetails() {
        return get("/user/details", Map.of());
    }

    public String resolveSetBoid(String setNumber) {
        if (setNumber == null || setNumber.isBlank()) {
            throw ApiException.badRequest("Set number is required to list on Brick Owl");
        }
        ApiException lastMissing = null;
        for (String candidate : setNumberCandidates(setNumber)) {
            JsonNode lookup = get("/catalog/id_lookup", Map.of(
                    "id", candidate,
                    "type", "Set",
                    "id_type", "set_number"
            ));
            try {
                return BrickOwlCatalog.resolveSetBoid(lookup, setNumber.trim());
            } catch (ApiException e) {
                if (e.getMessage() != null && e.getMessage().contains("no catalog match")) {
                    lastMissing = e;
                    continue;
                }
                throw e;
            }
        }
        throw lastMissing == null
                ? ApiException.badRequest("Brick Owl has no catalog match for set " + setNumber.trim())
                : lastMissing;
    }

    public String createLot(InventoryItem item, String boid) {
        MultiValueMap<String, String> body = form();
        body.add("boid", boid);
        body.add("quantity", String.valueOf(Math.max(item.getQuantity(), 1)));
        body.add("price", ChannelPrice.required(item, Platform.BRICKOWL));
        body.add("condition", item.getCondition().brickOwlCondition());
        if (item.getSku() != null && !item.getSku().isBlank()) {
            body.add("external_id", item.getSku());
        }
        JsonNode created = post("/inventory/create", body);
        String lotId = firstText(created, "lot_id", "id");
        if (lotId == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Brick Owl did not return a lot id");
        }
        return lotId;
    }

    public void updateLot(String lotId, InventoryItem item) {
        MultiValueMap<String, String> body = identifiedLot(lotId);
        body.add("absolute_quantity", String.valueOf(Math.max(item.getQuantity(), 0)));
        body.add("price", ChannelPrice.required(item, Platform.BRICKOWL));
        body.add("condition", item.getCondition().brickOwlCondition());
        if (item.getSku() != null && !item.getSku().isBlank()) {
            body.add("update_external_id_1", item.getSku());
        }
        post("/inventory/update", body);
    }

    public void setForSale(String lotId, boolean forSale) {
        MultiValueMap<String, String> body = identifiedLot(lotId);
        body.add("for_sale", forSale ? "1" : "0");
        post("/inventory/update", body);
    }

    public void deleteLot(String lotId) {
        if (lotId == null || lotId.isBlank()) {
            return;
        }
        try {
            MultiValueMap<String, String> body = identifiedLot(lotId);
            post("/inventory/delete", body);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return;
            }
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("not found") || message.contains("invalid lot")) {
                return;
            }
            throw e;
        }
    }

    public JsonNode getLot(String lotId) {
        JsonNode listed = get("/inventory/list", Map.of("lot_id", lotId, "active_only", "0"));
        JsonNode lot = firstLot(listed, lotId);
        if (lot == null) {
            throw ApiException.notFound("That Brick Owl lot was not found");
        }
        return lot;
    }

    public List<OrderBundle> fetchStoreOrdersSince(Instant since) {
        List<String> ids = listStoreOrderIds(since);
        List<OrderBundle> orders = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += BULK_ORDER_CHUNK) {
            orders.addAll(fetchOrderDetails(ids.subList(i, Math.min(i + BULK_ORDER_CHUNK, ids.size()))));
        }
        return orders;
    }

    List<String> listStoreOrderIds(Instant since) {
        List<String> ids = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("list_type", "store");
            query.put("sort_by", "updated");
            query.put("limit", String.valueOf(ORDER_PAGE_SIZE));
            query.put("offset", String.valueOf(offset));
            if (since != null) {
                query.put("update_time", String.valueOf(since.getEpochSecond()));
            }
            JsonNode page = get("/order/list", query);
            List<String> batch = orderIds(page);
            if (batch.isEmpty()) {
                return ids;
            }
            ids.addAll(batch);
            if (batch.size() < ORDER_PAGE_SIZE) {
                return ids;
            }
            offset += ORDER_PAGE_SIZE;
        }
    }

    private List<OrderBundle> fetchOrderDetails(List<String> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        try {
            return parseBulkOrders(post("/bulk/batch", bulkBody(orderIds)), orderIds);
        } catch (RuntimeException e) {
            log.info("Brick Owl bulk order fetch failed, loading orders one by one: {}", e.getMessage());
            List<OrderBundle> orders = new ArrayList<>();
            for (String orderId : orderIds) {
                try {
                    orders.add(new OrderBundle(get("/order/view", Map.of("order_id", orderId)), get("/order/items", Map.of("order_id", orderId))));
                } catch (RuntimeException inner) {
                    log.warn("Brick Owl order {} failed: {}", orderId, inner.getMessage());
                }
            }
            return orders;
        }
    }

    private MultiValueMap<String, String> bulkBody(List<String> orderIds) {
        ArrayNode requests = mapper.createArrayNode();
        for (String orderId : orderIds) {
            requests.add(bulkRequest("order/view", orderId));
            requests.add(bulkRequest("order/items", orderId));
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.set("requests", requests);
        MultiValueMap<String, String> body = form();
        body.add("requests", payload.toString());
        return body;
    }

    private ObjectNode bulkRequest(String endpoint, String orderId) {
        ObjectNode request = mapper.createObjectNode();
        request.put("endpoint", endpoint);
        request.put("request_method", "GET");
        ArrayNode params = request.putArray("params");
        ObjectNode param = params.addObject();
        param.put("order_id", orderId);
        return request;
    }

    static List<OrderBundle> parseBulkOrders(JsonNode root, List<String> orderIds) {
        JsonNode responses = root == null ? null : root.get("responses");
        if (responses == null || !responses.isArray()) {
            responses = root;
        }
        Map<String, JsonNode> views = new LinkedHashMap<>();
        Map<String, JsonNode> items = new LinkedHashMap<>();
        if (responses != null && responses.isArray()) {
            for (JsonNode response : responses) {
                JsonNode body = response.has("body") ? response.get("body") : response;
                String orderId = firstText(body, "order_id");
                if (orderId == null) {
                    continue;
                }
                if (looksLikeItems(body)) {
                    items.put(orderId, body);
                } else {
                    views.put(orderId, body);
                }
            }
        }
        List<OrderBundle> orders = new ArrayList<>();
        for (String orderId : orderIds) {
            JsonNode view = views.get(orderId);
            JsonNode lineItems = items.get(orderId);
            if (view != null) {
                orders.add(new OrderBundle(view, lineItems));
            }
        }
        return orders;
    }

    public static JsonNode firstLot(JsonNode listed, String lotId) {
        if (listed == null || listed.isNull() || listed.isMissingNode()) {
            return null;
        }
        if (listed.isObject() && (listed.has("lot_id") || listed.has("boid"))) {
            return listed;
        }
        JsonNode lots = listed.has("lots") ? listed.get("lots") : listed;
        if (lots != null && lots.isArray()) {
            for (JsonNode lot : lots) {
                if (lotId.equals(firstText(lot, "lot_id", "id"))) {
                    return lot;
                }
            }
            return lots.isEmpty() ? null : lots.get(0);
        }
        if (listed.isObject()) {
            JsonNode nested = listed.get(lotId);
            if (nested != null && nested.isObject()) {
                return nested;
            }
        }
        return null;
    }

    public static String lotUrl(JsonNode lot, String lotId, String boid) {
        String url = firstText(lot, "url", "lot_url", "permalink");
        if (url != null) {
            return url;
        }
        if (boid != null && !boid.isBlank()) {
            return "https://www.brickowl.com/boid/" + boid.trim();
        }
        if (lotId == null || lotId.isBlank()) {
            return null;
        }
        return "https://www.brickowl.com/inventory/" + lotId.trim();
    }

    public static boolean lotForSale(JsonNode lot) {
        JsonNode value = lot == null ? null : lot.get("for_sale");
        if (value == null || value.isNull() || value.isMissingNode()) {
            return true;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt() != 0;
        }
        String text = value.asText();
        return !"0".equals(text) && !"false".equalsIgnoreCase(text);
    }

    private static boolean looksLikeItems(JsonNode body) {
        if (body == null) {
            return false;
        }
        if (body.isArray()) {
            return true;
        }
        return body.has("items") || body.has("order_items");
    }

    private static List<String> orderIds(JsonNode page) {
        List<String> ids = new ArrayList<>();
        JsonNode orders = page;
        if (page != null && page.has("orders")) {
            orders = page.get("orders");
        }
        if (orders == null || !orders.isArray()) {
            return ids;
        }
        for (JsonNode order : orders) {
            String id = firstText(order, "order_id", "id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static List<String> setNumberCandidates(String setNumber) {
        String trimmed = setNumber.trim();
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        if (trimmed.matches(".*-1$")) {
            candidates.add(trimmed.substring(0, trimmed.length() - 2));
        } else if (!trimmed.contains("-")) {
            candidates.add(trimmed + "-1");
        }
        return candidates;
    }

    private MultiValueMap<String, String> identifiedLot(String lotId) {
        if (lotId == null || lotId.isBlank()) {
            throw ApiException.badRequest("Brick Owl lot id is missing");
        }
        MultiValueMap<String, String> body = form();
        body.add("lot_id", lotId.trim());
        return body;
    }

    private MultiValueMap<String, String> form() {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("key", config.getApiKey());
        return body;
    }

    private JsonNode get(String path, Map<String, String> query) {
        requireConfigured();
        recordApiCall();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(config.host() + path)
                .queryParam("key", config.getApiKey());
        query.forEach(builder::queryParam);
        return exchange(restClient.get().uri(builder.build(true).toUri()).headers(this::headers).retrieve());
    }

    private JsonNode post(String path, MultiValueMap<String, String> body) {
        requireConfigured();
        recordApiCall();
        return exchange(restClient.post()
                .uri(config.host() + path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(this::headers)
                .body(body)
                .retrieve());
    }

    private void headers(org.springframework.http.HttpHeaders headers) {
        String agent = config.getUserAgent() == null || config.getUserAgent().isBlank()
                ? "TheTimelessVault/1.0"
                : config.getUserAgent().trim();
        headers.set("User-Agent", agent);
        headers.set("Accept", "application/json");
    }

    private JsonNode exchange(RestClient.ResponseSpec spec) {
        String raw;
        try {
            raw = spec.body(String.class);
        } catch (RestClientResponseException e) {
            raw = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 404) {
                throw ApiException.notFound(errorMessage(raw, "Brick Owl resource not found"));
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw ApiException.unavailable(errorMessage(raw, "Brick Owl rejected the API key"));
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, errorMessage(raw, "Brick Owl error: " + e.getStatusCode().value()));
        }
        if (raw == null || raw.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode root = mapper.readTree(raw);
            String error = firstText(root, "error", "message");
            if (error != null && !root.has("order_id") && !root.has("lot_id") && !root.has("boids")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Brick Owl error: " + error);
            }
            return root;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse Brick Owl response");
        }
    }

    private static String errorMessage(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(raw);
            String error = firstText(root, "error", "message");
            return error == null ? fallback : "Brick Owl error: " + error;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw ApiException.unavailable("Brick Owl is not configured");
        }
    }

    private void recordApiCall() {
        if (apiCalls != null) {
            apiCalls.record(API_CALLS);
        }
    }

    public static String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || value.isMissingNode()) {
                continue;
            }
            String text = value.asText();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    public record OrderBundle(JsonNode order, JsonNode items) {
    }
}
