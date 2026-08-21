package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.ChannelPrice;
import com.thetimelessvault.inventory.InventoryDtos;
import com.thetimelessvault.inventory.InventoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class BrickLinkClient {

    static final String DEFAULT_STOCK_ROOM_ID = "A";
    private static final Logger log = LoggerFactory.getLogger(BrickLinkClient.class);
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final AppProperties.Bricklink config;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final RestClient publicClient;

    @Autowired
    public BrickLinkClient(AppProperties properties, ObjectMapper mapper) {
        this(properties, mapper, RestClient.builder().build(), publicRestClient());
    }

    BrickLinkClient(AppProperties properties, ObjectMapper mapper, RestClient restClient, RestClient publicClient) {
        this.config = properties.getBricklink();
        this.mapper = mapper;
        this.restClient = restClient;
        this.publicClient = publicClient;
    }

    public boolean configured() {
        return config.configured();
    }

    public JsonNode listReceivedOrders(boolean filed) {
        if (!filed) {
            return request("GET", "/orders", null);
        }
        return request("GET", "/orders?direction=in&filed=true", null);
    }

    public JsonNode getOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw ApiException.badRequest("BrickLink order id is missing");
        }
        return request("GET", "/orders/" + orderId, null);
    }

    public JsonNode getOrderItems(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw ApiException.badRequest("BrickLink order id is missing");
        }
        return request("GET", "/orders/" + orderId + "/items", null);
    }

    public JsonNode createInventory(InventoryItem item) {
        return request("POST", "/inventories", inventoryRequest(item).toString());
    }

    public JsonNode updateInventory(String inventoryId, InventoryItem item) {
        if (inventoryId == null || inventoryId.isBlank()) {
            throw ApiException.badRequest("BrickLink inventory id is missing");
        }
        JsonNode current = request("GET", "/inventories/" + inventoryId, null);
        int currentQty = current.path("quantity").asInt(0);
        int desiredQty = Math.max(item.getQuantity(), 0);
        return request("PUT", "/inventories/" + inventoryId, inventoryUpdateRequest(item, desiredQty - currentQty).toString());
    }

    ObjectNode inventoryRequest(InventoryItem item) {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode catalog = body.putObject("item");
        catalog.put("no", item.getCatalogItem().getSetNumber());
        catalog.put("type", "SET");
        body.put("color_id", 0);
        body.put("quantity", item.getQuantity());
        body.put("unit_price", ChannelPrice.required(item, Platform.BRICKLINK));
        body.put("new_or_used", item.getCondition().brickLinkNewOrUsed());
        body.put("completeness", item.getCondition().brickLinkCompleteness());
        body.put("description", com.thetimelessvault.common.DescriptionHtml.forBrickLink(item.getShortDescription()));
        body.put("remarks", item.getSku());
        body.put("bulk", 1);
        body.put("is_retain", false);
        body.put("is_stock_room", true);
        body.put("stock_room_id", DEFAULT_STOCK_ROOM_ID);
        return body;
    }

    ObjectNode inventoryUpdateRequest(InventoryItem item, int quantityDelta) {
        ObjectNode body = mapper.createObjectNode();
        if (quantityDelta != 0) {
            body.put("quantity", quantityDelta);
        }
        body.put("unit_price", ChannelPrice.required(item, Platform.BRICKLINK));
        body.put("new_or_used", item.getCondition().brickLinkNewOrUsed());
        body.put("completeness", item.getCondition().brickLinkCompleteness());
        body.put("description", com.thetimelessvault.common.DescriptionHtml.forBrickLink(item.getShortDescription()));
        body.put("remarks", item.getSku());
        return body;
    }

    public boolean updateStockRoom(String inventoryId, boolean inStockRoom) {
        JsonNode data = request("PUT", "/inventories/" + inventoryId, stockRoomRequest(inStockRoom).toString());
        if (data != null && data.hasNonNull("is_stock_room")) {
            return data.path("is_stock_room").asBoolean(inStockRoom);
        }
        return inStockRoom;
    }

    public void deleteInventory(String inventoryId) {
        if (inventoryId == null || inventoryId.isBlank()) {
            return;
        }
        try {
            request("DELETE", "/inventories/" + inventoryId, null);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return;
            }
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("RESOURCE_NOT_FOUND") || message.contains("not found")) {
                return;
            }
            throw e;
        }
    }

    ObjectNode stockRoomRequest(boolean inStockRoom) {
        ObjectNode body = mapper.createObjectNode();
        body.put("is_stock_room", inStockRoom);
        if (inStockRoom) {
            body.put("stock_room_id", DEFAULT_STOCK_ROOM_ID);
        }
        return body;
    }

    public JsonNode priceGuide(String setNumber, String newOrUsed) {
        return request("GET", "/items/SET/" + setNumber + "/price?guide_type=stock&new_or_used=" + newOrUsed + "&currency_code=USD", null);
    }

    public List<BrickLinkForSale.Lot> forSaleNewSealedShipsToUsa(String setNumber) {
        Long itemId = publicCatalogItemId(setNumber);
        if (itemId == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "BrickLink blocked the catalog lookup from this server.");
        }
        List<BrickLinkForSale.Lot> lots = new ArrayList<>();
        int page = 1;
        int total = Integer.MAX_VALUE;
        while (lots.size() < total) {
            JsonNode root = catalogIfs(itemId, setNumber, page);
            int returnCode = root.path("returnCode").asInt(-1);
            if (returnCode != 0) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "BrickLink listing lookup failed (returnCode " + returnCode + ").");
            }
            total = root.path("total_count").asInt(0);
            List<BrickLinkForSale.Lot> pageLots = BrickLinkForSale.parseLots(root, setNumber);
            lots.addAll(pageLots);
            if (pageLots.isEmpty() || root.path("list").size() < 200) {
                break;
            }
            page++;
        }
        return lots;
    }

    private Long publicCatalogItemId(String setNumber) {
        for (String candidate : setNumberCandidates(setNumber)) {
            Long itemId = BrickLinkForSale.parseItemId(publicSearchJson(candidate));
            if (itemId != null) {
                return itemId;
            }
        }
        for (String candidate : setNumberCandidates(setNumber)) {
            Long itemId = BrickLinkForSale.parseItemId(publicCatalogHtml(candidate));
            if (itemId != null) {
                return itemId;
            }
        }
        log.warn("BrickLink catalog id missing for {}", setNumber);
        return null;
    }

    private JsonNode catalogIfs(long itemId, String setNumber, int page) {
        String uri = "https://www.bricklink.com/ajax/clone/catalogifs.ajax"
                + "?itemid=" + itemId
                + "&color=-1"
                + "&st=1"
                + "&ss=US"
                + "&cond=N"
                + "&minqty=0"
                + "&nmp=0"
                + "&nosuperlot=1"
                + "&ii=0"
                + "&ic=0"
                + "&is=1"
                + "&loc="
                + "&reg=0"
                + "&rpp=200"
                + "&pi=" + page;
        try {
            String raw = fetchPublic(
                    uri,
                    "application/json",
                    "https://www.bricklink.com/v2/catalog/catalogitem.page?S=" + urlEncode(setNumber)
            );
            if (raw == null || !raw.stripLeading().startsWith("{")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "BrickLink listing lookup was blocked or returned HTML instead of listings.");
            }
            return mapper.readTree(raw);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not read BrickLink listings");
        }
    }

    public InventoryDtos.BrickLinkPackage packageForSet(String setNumber) {
        if (setNumber == null || setNumber.isBlank()) {
            return null;
        }
        if (!configured()) {
            return packageFromPublicCatalog(setNumber);
        }
        JsonNode item = null;
        for (String candidate : setNumberCandidates(setNumber)) {
            try {
                item = request("GET", "/items/SET/" + candidate, null);
                if (item != null && !item.isMissingNode()) {
                    break;
                }
            } catch (Exception ignored) {
                // Try the next catalog number shape (7665 vs 7665-1).
            }
        }
        if (item != null && !item.isMissingNode()) {
            InventoryDtos.BrickLinkPackage fromApi = toPackage(
                    decimal(item, "weight"),
                    decimal(item, "dim_x"),
                    decimal(item, "dim_y"),
                    decimal(item, "dim_z")
            );
            if (fromApi != null) {
                return fromApi;
            }
        }
        return packageFromPublicCatalog(setNumber);
    }

    private InventoryDtos.BrickLinkPackage packageFromPublicCatalog(String setNumber) {
        for (String candidate : setNumberCandidates(setNumber)) {
            var parsed = BrickLinkMeasurements.parsePublicCatalog(publicCatalogHtml(candidate));
            if (parsed != null) {
                return toPackage(parsed.grams(), parsed.lengthCm(), parsed.widthCm(), parsed.heightCm());
            }
        }
        return null;
    }

    private String publicSearchJson(String setNumber) {
        return fetchPublic(
                "https://www.bricklink.com/ajax/clone/search/searchproduct.ajax?q="
                        + urlEncode(setNumber)
                        + "&type=S",
                "application/json",
                "https://www.bricklink.com/"
        );
    }

    private String publicCatalogHtml(String setNumber) {
        return fetchPublic(
                "https://www.bricklink.com/v2/catalog/catalogitem.page?S=" + urlEncode(setNumber),
                "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
                "https://www.bricklink.com/"
        );
    }

    private String fetchPublic(String uri, String accept, String referer) {
        try {
            return publicClient.get()
                    .uri(URI.create(uri))
                    .header("Accept", accept)
                    .header("Referer", referer)
                    .header("Origin", "https://www.bricklink.com")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("BrickLink {} returned {} ({})", uri, e.getStatusCode().value(), preview(e.getResponseBodyAsString()));
            return null;
        } catch (Exception e) {
            log.warn("BrickLink {} failed: {}", uri, e.toString());
            return null;
        }
    }

    private static RestClient publicRestClient() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .cookieHandler(cookies)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", BROWSER_UA)
                .defaultHeader("Accept-Language", "en-US,en;q=0.9")
                .build();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String preview(String body) {
        if (body == null || body.isBlank()) {
            return "empty";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.substring(0, Math.min(compact.length(), 180));
    }

    private static InventoryDtos.BrickLinkPackage toPackage(BigDecimal grams, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        int[] weight = BrickLinkMeasurements.poundsAndOunces(grams);
        BigDecimal length = BrickLinkMeasurements.cmToInches(lengthCm);
        BigDecimal width = BrickLinkMeasurements.cmToInches(widthCm);
        BigDecimal height = BrickLinkMeasurements.cmToInches(heightCm);
        if (weight[0] == 0 && weight[1] == 0 && length == null && width == null && height == null) {
            return null;
        }
        InventoryDtos.BrickLinkMeasures original = new InventoryDtos.BrickLinkMeasures(
                weight[0], weight[1], length, width, height
        );
        InventoryDtos.BrickLinkMeasures shipping = new InventoryDtos.BrickLinkMeasures(
                BrickLinkMeasurements.shippingPounds(weight[0], weight[1]),
                0,
                BrickLinkMeasurements.shippingInches(length),
                BrickLinkMeasurements.shippingInches(width),
                BrickLinkMeasurements.shippingInches(height)
        );
        return new InventoryDtos.BrickLinkPackage(shipping, original);
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

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode request(String method, String path, String jsonBody) {
        if (!config.configured()) {
            throw ApiException.unavailable("BrickLink is not configured");
        }
        String url = config.getBaseUrl() + path;
        String auth = OAuth1Signer.authorizationHeader(
                method, url, config.getConsumerKey(), config.getConsumerSecret(), config.getToken(), config.getTokenSecret()
        );
        var spec = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(url)
                .header("Authorization", auth)
                .header("Accept", "application/json");
        String raw;
        try {
            if (jsonBody != null) {
                raw = spec.contentType(MediaType.APPLICATION_JSON).body(jsonBody).retrieve().body(String.class);
            } else {
                raw = spec.retrieve().body(String.class);
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw ApiException.notFound("BrickLink resource not found");
            }
            raw = e.getResponseBodyAsString();
            if (raw == null || raw.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "BrickLink error: " + e.getStatusCode().value() + " " + e.getStatusText());
            }
        }
        if (raw == null || raw.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode root = mapper.readTree(raw);
            int code = root.path("meta").path("code").asInt(200);
            if (code < 200 || code >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "BrickLink error: " + root.path("meta").path("description").asText(raw));
            }
            return root.path("data");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse BrickLink response");
        }
    }
}
