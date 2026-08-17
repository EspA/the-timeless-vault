package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.inventory.ChannelPrice;
import com.thetimelessvault.inventory.InventoryItem;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EbayClient {

    private static final Logger log = LoggerFactory.getLogger(EbayClient.class);

    private final AppProperties.Ebay config;
    private final EbayTokenService tokens;
    private final ObjectMapper mapper;
    private final AppSettingRepository settings;
    private final RestClient restClient = RestClient.builder().build();

    private volatile EbaySellDefaults cachedSellDefaults;

    public EbayClient(AppProperties properties, EbayTokenService tokens, ObjectMapper mapper) {
        this(properties, tokens, mapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EbayClient(
            AppProperties properties,
            EbayTokenService tokens,
            ObjectMapper mapper,
            AppSettingRepository settings
    ) {
        this.config = properties.getEbay();
        this.tokens = tokens;
        this.mapper = mapper;
        this.settings = settings;
    }

    public boolean configured() {
        return config.configured();
    }

    public boolean browseConfigured() {
        return config.browseConfigured();
    }

    public boolean sellReady() {
        return config.configured() && tokens != null && tokens.hasRefreshToken();
    }

    public List<Map<String, String>> storeCategories() {
        if (!sellReady()) {
            return EbayStoreCategories.fallback();
        }
        try {
            JsonNode data = sell("GET", "/sell/stores/v1/store/categories", null);
            List<Map<String, String>> categories = EbayStoreCategories.flatten(data);
            return categories.isEmpty() ? EbayStoreCategories.fallback() : categories;
        } catch (Exception e) {
            log.warn("Could not load eBay store categories: {}", e.getMessage());
            return EbayStoreCategories.fallback();
        }
    }

    public void createOrReplaceInventoryItem(InventoryItem item, List<String> photoUrls) {
        EbayCatalogTemplate template = resolveCatalogTemplate(item);
        try {
            putInventoryItem(item, photoUrls, template);
        } catch (ApiException e) {
            if (!isEbayInternalError(e)) {
                throw e;
            }
            log.warn("eBay inventory replace failed with 25001 for {}; retrying with a simpler payload", item.getSku());
            try {
                putInventoryItem(item, photoUrls, template.withoutCopiedAspects());
            } catch (ApiException retry) {
                if (!isEbayInternalError(retry) || template.epid() == null) {
                    throw retry;
                }
                log.warn("eBay inventory replace still failing for {}; retrying without ePID", item.getSku());
                putInventoryItem(item, photoUrls, EbayCatalogTemplate.fromCatalog(item));
            }
        }
    }

    private void putInventoryItem(InventoryItem item, List<String> photoUrls, EbayCatalogTemplate template) {
        ObjectNode body = inventoryItemRequest(item, photoUrls, template);
        sell("PUT", "/sell/inventory/v1/inventory_item/" + skuPath(item.getSku()) + "?locale=en_US", body.toString());
    }

    static boolean isEbayInternalError(ApiException error) {
        String message = error.getMessage();
        return message != null && message.contains("[25001]");
    }

    ObjectNode inventoryItemRequest(InventoryItem item, List<String> photoUrls, EbayCatalogTemplate template) {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode availability = body.putObject("availability").putObject("shipToLocationAvailability");
        availability.put("quantity", item.getQuantity());
        body.put("condition", item.getCondition().ebayCondition());
        ObjectNode product = body.putObject("product");
        product.put("title", item.getTitle());
        String description = com.thetimelessvault.common.DescriptionHtml.forEbayProduct(item.getDescription());
        product.put("description", description.isBlank() ? item.getTitle() : description);
        ArrayNode images = product.putArray("imageUrls");
        photoUrls.forEach(images::add);
        applyCatalogTemplate(product, template);
        ObjectNode pkg = body.putObject("packageWeightAndSize");
        pkg.put("packageType", "PACKAGE_THICK_ENVELOPE");
        if (hasPositiveDimension(item.getPackageLength())
                && hasPositiveDimension(item.getPackageWidth())
                && hasPositiveDimension(item.getPackageHeight())) {
            ObjectNode dim = pkg.putObject("dimensions");
            dim.put("length", item.getPackageLength().doubleValue());
            dim.put("width", item.getPackageWidth().doubleValue());
            dim.put("height", item.getPackageHeight().doubleValue());
            dim.put("unit", "INCH");
        }
        ObjectNode weight = pkg.putObject("weight");
        weight.put("value", pounds(item.getPackageLbs(), item.getPackageOz()).doubleValue());
        weight.put("unit", "POUND");
        return body;
    }

    public EbayCatalogPreview previewCatalog(InventoryItem item) {
        return EbayCatalogPreview.from(item, resolveCatalogTemplate(item));
    }

    EbayCatalogTemplate resolveCatalogTemplate(InventoryItem item) {
        EbayCatalogTemplate fallback = EbayCatalogTemplate.fromCatalog(item);
        if (tokens == null || !config.browseConfigured()) {
            return fallback;
        }
        try {
            JsonNode search = searchCatalogListings(item.getCatalogItem());
            List<JsonNode> summaries = EbayCatalogTemplate.matchingSummaries(search, item.getCatalogItem());
            List<JsonNode> details = new ArrayList<>();
            for (JsonNode summary : EbayCatalogTemplate.detailCandidates(summaries)) {
                JsonNode detail = getBrowseItem(summary);
                if (detail != null && !detail.isEmpty()) {
                    details.add(detail);
                }
            }
            EbayCatalogTemplate merged = EbayCatalogTemplate.merge(fallback, summaries, details);
            if (merged.epid() != null) {
                log.info("Using eBay catalog template epid={} for set {}", merged.epid(), item.getCatalogItem().getSetNumber());
            } else if (details.isEmpty()) {
                log.info("No matching eBay catalog listing for set {}; using BrickEconomy aspects", item.getCatalogItem().getSetNumber());
            }
            return merged;
        } catch (Exception e) {
            log.warn("eBay catalog template lookup failed for set {}", item.getCatalogItem().getSetNumber(), e);
            return fallback;
        }
    }

    private void applyCatalogTemplate(ObjectNode product, EbayCatalogTemplate template) {
        if (template == null) {
            return;
        }
        product.put("brand", "LEGO");
        if (template.epid() != null && !template.epid().isBlank()) {
            product.put("epid", template.epid());
        }
        if (template.mpn() != null && !template.mpn().isBlank()) {
            product.put("mpn", template.mpn());
        }
        if (!template.upc().isEmpty()) {
            ArrayNode upc = product.putArray("upc");
            template.upc().forEach(upc::add);
        } else if (!template.ean().isEmpty()) {
            ArrayNode ean = product.putArray("ean");
            template.ean().forEach(ean::add);
        }
        ObjectNode aspects = product.putObject("aspects");
        boolean catalogProduct = template.epid() != null && !template.epid().isBlank();
        template.aspects().forEach((name, values) -> {
            if (catalogProduct && !EbayCatalogTemplate.includeWithCatalogProduct(name)) {
                return;
            }
            ArrayNode array = aspects.putArray(name);
            values.forEach(array::add);
        });
    }

    public String hostImage(byte[] bytes, String filename, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("eBay photo is empty");
        }
        try {
            return hostImageViaMediaApi(bytes, filename, contentType);
        } catch (ApiException ignored) {
            return hostImageViaTradingApi(bytes, filename, contentType);
        }
    }

    static boolean isPublicHttpsImageUrl(String url) {
        if (url == null || !url.startsWith("https://")) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String lower = host.toLowerCase(java.util.Locale.ROOT);
            return !lower.equals("localhost")
                    && !lower.equals("127.0.0.1")
                    && !lower.endsWith(".local")
                    && !lower.endsWith(".internal");
        } catch (Exception e) {
            return false;
        }
    }

    public String createOffer(InventoryItem item) {
        ObjectNode body = offerRequest(item, sellDefaults());
        String existing = findOfferId(item.getSku());
        if (existing != null && !existing.isBlank()) {
            sell("PUT", "/sell/inventory/v1/offer/" + existing, body.toString());
            return existing;
        }
        JsonNode created = sell("POST", "/sell/inventory/v1/offer", body.toString());
        String offerId = created.path("offerId").asText(null);
        if (offerId == null || offerId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay did not return an offer id");
        }
        return offerId;
    }

    public String liveListingId(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return null;
        }
        JsonNode offer = sell("GET", "/sell/inventory/v1/offer/" + offerId, null);
        String listingId = offer.path("listing").path("listingId").asText(null);
        String listingStatus = offer.path("listing").path("listingStatus").asText("");
        if (!"PUBLISHED".equalsIgnoreCase(offer.path("status").asText(""))
                || listingId == null
                || listingId.isBlank()
                || "ENDED".equalsIgnoreCase(listingStatus)) {
            return null;
        }
        return listingId;
    }

    public List<String> existingImageUrls(String sku) {
        if (sku == null || sku.isBlank()) {
            return List.of();
        }
        try {
            JsonNode images = sell("GET", "/sell/inventory/v1/inventory_item/" + skuPath(sku), null)
                    .path("product").path("imageUrls");
            List<String> urls = new ArrayList<>();
            if (images.isArray()) {
                for (JsonNode image : images) {
                    String url = image.asText("");
                    if (isPublicHttpsImageUrl(url)) {
                        urls.add(url);
                    }
                }
            }
            return urls;
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return List.of();
            }
            throw e;
        }
    }

    ObjectNode offerRequest(InventoryItem item) {
        return offerRequest(item, sellDefaults());
    }

    ObjectNode offerRequest(InventoryItem item, EbaySellDefaults defaults) {
        ObjectNode body = mapper.createObjectNode();
        body.put("sku", item.getSku());
        body.put("marketplaceId", config.getMarketplaceId());
        body.put("format", "FIXED_PRICE");
        body.put("availableQuantity", item.getQuantity());
        body.put("categoryId", config.getCategoryId());
        body.put("merchantLocationKey", defaults.merchantLocationKey());
        ObjectNode price = body.putObject("pricingSummary").putObject("price");
        price.put("value", ChannelPrice.required(item, Platform.EBAY));
        price.put("currency", "USD");
        ObjectNode policies = body.putObject("listingPolicies");
        policies.put("fulfillmentPolicyId", defaults.fulfillmentPolicyId());
        policies.put("paymentPolicyId", defaults.paymentPolicyId());
        policies.put("returnPolicyId", defaults.returnPolicyId());
        if (item.getMinimumOffer() != null) {
            ObjectNode best = policies.putObject("bestOfferTerms");
            best.put("bestOfferEnabled", true);
            ObjectNode min = best.putObject("autoDeclinePrice");
            min.put("value", item.getMinimumOffer().toPlainString());
            min.put("currency", "USD");
        }
        String listingDescription = com.thetimelessvault.common.DescriptionHtml.forEbayListing(item.getDescription());
        if (!listingDescription.isBlank()) {
            body.put("listingDescription", listingDescription);
        }
        if (item.getEbayStoreCategory() != null && !item.getEbayStoreCategory().isBlank()) {
            body.putArray("storeCategoryNames").add("/" + item.getEbayStoreCategory());
        }
        return body;
    }

    public String findOfferId(String sku) {
        List<String> offerIds = findOfferIds(sku);
        return offerIds.isEmpty() ? null : offerIds.getFirst();
    }

    public List<String> findOfferIds(String sku) {
        if (sku == null || sku.isBlank()) {
            return List.of();
        }
        try {
            JsonNode data = sell("GET", "/sell/inventory/v1/offer?sku=" + skuPath(sku), null);
            JsonNode offers = data.path("offers");
            List<String> offerIds = new ArrayList<>();
            if (offers.isArray()) {
                for (JsonNode offer : offers) {
                    String offerId = offer.path("offerId").asText(null);
                    if (offerId != null && !offerId.isBlank()) {
                        offerIds.add(offerId);
                        log.info(
                                "eBay offer sku={} offerId={} status={} listingId={} listingStatus={}",
                                sku,
                                offerId,
                                offer.path("status").asText(""),
                                offer.path("listing").path("listingId").asText(""),
                                offer.path("listing").path("listingStatus").asText("")
                        );
                    }
                }
            }
            return offerIds;
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return List.of();
            }
            throw e;
        }
    }

    public void purgeSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw ApiException.badRequest("Missing SKU");
        }
        log.info("Purging eBay inventory for sku={}", sku);
        // Delete the SKU while the listing may still be live. eBay documents this as
        // removing associated single-variation listings, not merely ending them.
        // withdrawOffer first would leave an Inactive row in Seller Hub with no API to clear it.
        deleteInventoryItem(sku);
        for (String offerId : findOfferIds(sku)) {
            deleteOffer(offerId);
        }
        deleteInventoryItem(sku);
        List<String> remaining = findOfferIds(sku);
        if (!remaining.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "eBay still has " + remaining.size() + " offer(s) for this SKU after delete"
            );
        }
    }

    public String publishOffer(String offerId) {
        JsonNode published = sell("POST", "/sell/inventory/v1/offer/" + offerId + "/publish", "{}");
        String listingId = published.path("listingId").asText(null);
        if (listingId == null || listingId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay did not return a listing id");
        }
        return listingId;
    }

    public void withdrawOffer(String offerId) {
        sell("POST", "/sell/inventory/v1/offer/" + offerId + "/withdraw", "{}");
    }

    public void deleteOffer(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return;
        }
        try {
            sell("DELETE", "/sell/inventory/v1/offer/" + offerId, null);
        } catch (ApiException e) {
            if (e.getStatus() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }
    }

    public void deleteInventoryItem(String sku) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        try {
            sell("DELETE", "/sell/inventory/v1/inventory_item/" + skuPath(sku), null);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw e;
        }
    }

    public static String listingUrl(String listingId) {
        if (listingId == null || listingId.isBlank()) {
            return null;
        }
        return "https://www.ebay.com/itm/" + listingId;
    }

    public EbaySellDefaults sellDefaults() {
        if (tokens == null) {
            return configDefaults();
        }
        EbaySellDefaults cached = cachedSellDefaults;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedSellDefaults != null) {
                return cachedSellDefaults;
            }
            cachedSellDefaults = resolveSellDefaults();
            return cachedSellDefaults;
        }
    }

    private EbaySellDefaults configDefaults() {
        return new EbaySellDefaults(
                config.getMerchantLocationKey(),
                config.getFulfillmentPolicyId(),
                config.getPaymentPolicyId(),
                config.getReturnPolicyId()
        );
    }

    private EbaySellDefaults resolveSellDefaults() {
        String marketplace = URLEncoder.encode(config.getMarketplaceId(), StandardCharsets.UTF_8);
        String locationKey = EbaySellDefaults.pick(
                sell("GET", "/sell/inventory/v1/location?limit=50", null).path("locations"),
                config.getMerchantLocationKey(),
                "merchantLocationKey",
                "name",
                "merchantLocationStatus",
                "ENABLED"
        );
        String fulfillmentId = EbaySellDefaults.pick(
                sell("GET", "/sell/account/v1/fulfillment_policy?marketplace_id=" + marketplace, null)
                        .path("fulfillmentPolicies"),
                config.getFulfillmentPolicyId(),
                "fulfillmentPolicyId",
                "name",
                null,
                null
        );
        String paymentId = EbaySellDefaults.pick(
                sell("GET", "/sell/account/v1/payment_policy?marketplace_id=" + marketplace, null)
                        .path("paymentPolicies"),
                config.getPaymentPolicyId(),
                "paymentPolicyId",
                "name",
                null,
                null
        );
        String returnId = EbaySellDefaults.pick(
                sell("GET", "/sell/account/v1/return_policy?marketplace_id=" + marketplace, null)
                        .path("returnPolicies"),
                config.getReturnPolicyId(),
                "returnPolicyId",
                "name",
                null,
                null
        );
        if (locationKey == null || locationKey.isBlank()) {
            throw ApiException.unavailable("No enabled eBay inventory location found. Create one from Settings — Seller Hub does not expose this for API listings.");
        }
        if (fulfillmentId == null || fulfillmentId.isBlank()) {
            throw ApiException.unavailable("No eBay fulfillment (shipping) policy found. Create business policies in Seller Hub, then retry.");
        }
        if (paymentId == null || paymentId.isBlank()) {
            throw ApiException.unavailable("No eBay payment policy found. Create business policies in Seller Hub, then retry.");
        }
        if (returnId == null || returnId.isBlank()) {
            throw ApiException.unavailable("No eBay return policy found. Create business policies in Seller Hub, then retry.");
        }
        return new EbaySellDefaults(locationKey, fulfillmentId, paymentId, returnId);
    }

    public void clearSellDefaultsCache() {
        cachedSellDefaults = null;
    }

    public String ensureWarehouseLocation(java.util.Map<String, String> requested) {
        JsonNode existing = sell("GET", "/sell/inventory/v1/location?limit=50", null).path("locations");
        String enabled = EbaySellDefaults.pick(
                existing,
                config.getMerchantLocationKey(),
                "merchantLocationKey",
                "name",
                "merchantLocationStatus",
                "ENABLED"
        );
        if (enabled != null && !enabled.isBlank()) {
            return enabled;
        }
        if (existing != null && existing.isArray()) {
            for (JsonNode loc : existing) {
                if ("DISABLED".equalsIgnoreCase(loc.path("merchantLocationStatus").asText())) {
                    String key = loc.path("merchantLocationKey").asText("");
                    if (!key.isBlank()) {
                        sell("POST", "/sell/inventory/v1/location/"
                                + URLEncoder.encode(key, StandardCharsets.UTF_8) + "/enable", "{}");
                        clearSellDefaultsCache();
                        return key;
                    }
                }
            }
        }
        String name = value(requested, "name", "Private Mail Box");
        String postal = value(requested, "postalCode", "");
        String city = value(requested, "city", "");
        String state = value(requested, "stateOrProvince", "");
        String country = value(requested, "country", "US");
        String line1 = value(requested, "addressLine1", "");
        if (postal.isBlank() && (city.isBlank() || state.isBlank())) {
            throw ApiException.badRequest("Enter a ZIP code, or city and state, for the eBay warehouse location.");
        }
        String key = merchantLocationKey(name);
        ObjectNode body = mapper.createObjectNode();
        body.put("name", name);
        body.put("merchantLocationStatus", "ENABLED");
        body.putArray("locationTypes").add("WAREHOUSE");
        ObjectNode address = body.putObject("location").putObject("address");
        address.put("country", country.isBlank() ? "US" : country);
        if (!postal.isBlank()) {
            address.put("postalCode", postal);
        }
        if (!city.isBlank()) {
            address.put("city", city);
        }
        if (!state.isBlank()) {
            address.put("stateOrProvince", state);
        }
        if (!line1.isBlank()) {
            address.put("addressLine1", line1);
        }
        sell("POST", "/sell/inventory/v1/location/" + URLEncoder.encode(key, StandardCharsets.UTF_8), body.toString());
        clearSellDefaultsCache();
        return key;
    }

    static String merchantLocationKey(String name) {
        String key = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        key = key.replaceAll("^-+", "").replaceAll("-+$", "");
        if (key.isBlank()) {
            key = "warehouse";
        }
        return key.length() > 36 ? key.substring(0, 36) : key;
    }

    private static String value(java.util.Map<String, String> requested, String key, String fallback) {
        if (requested == null) {
            return fallback;
        }
        String raw = requested.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    /**
     * Market and catalog searches match by keywords only. Never pass category_ids —
     * sellers often list sealed sets outside Complete Sets &amp; Packs.
     */
    public JsonNode searchBrowse(String query) {
        return searchBrowse(query, null);
    }

    public JsonNode searchBrowse(String query, String excludeWords) {
        String q = EbayMarketFilters.browseQuery(query);
        String filter = "conditionIds:{1000},buyingOptions:{AUCTION|FIXED_PRICE|BEST_OFFER}";
        String path = "/buy/browse/v1/item_summary/search?q="
                + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&limit=200"
                + "&filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);
        String zip = buyerPostalCode();
        if (!zip.isBlank()) {
            path += "&buyerPostalCode=" + URLEncoder.encode(zip, StandardCharsets.UTF_8);
        }
        return browseGet(path);
    }

    JsonNode searchCatalogListings(CatalogItem catalog) {
        ObjectNode combined = mapper.createObjectNode();
        ArrayNode summaries = combined.putArray("itemSummaries");
        Set<String> seen = new LinkedHashSet<>();
        String upc = EbayCatalogTemplate.digits(catalog.getUpc());
        String ean = EbayCatalogTemplate.digits(catalog.getEan());
        if (!upc.isBlank()) {
            addSummaries(summaries, seen, browseGetOrEmpty("/buy/browse/v1/item_summary/search?gtin="
                    + URLEncoder.encode(upc, StandardCharsets.UTF_8) + "&limit=20"));
        }
        if (summaries.isEmpty() && !ean.isBlank()) {
            addSummaries(summaries, seen, browseGetOrEmpty("/buy/browse/v1/item_summary/search?gtin="
                    + URLEncoder.encode(ean, StandardCharsets.UTF_8) + "&limit=20"));
        }
        String setNumber = ThemeMapper.displaySetNumber(catalog.getSetNumber());
        if (!setNumber.isBlank()) {
            addSummaries(summaries, seen, browseGetOrEmpty("/buy/browse/v1/item_summary/search?q="
                    + URLEncoder.encode("LEGO " + setNumber, StandardCharsets.UTF_8)
                    + "&limit=20"));
        }
        return combined;
    }

    JsonNode getBrowseItem(JsonNode summary) {
        if (summary == null) {
            return mapper.createObjectNode();
        }
        String href = summary.path("itemHref").asText("");
        if (!href.isBlank()) {
            try {
                return browseUri(URI.create(href));
            } catch (Exception e) {
                log.warn("Could not load eBay listing {}", href);
            }
        }
        return getBrowseItem(summary.path("itemId").asText(""));
    }

    JsonNode getBrowseItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return browseUri(URI.create(config.browseApiHost() + "/buy/browse/v1/item/"
                    + URLEncoder.encode(itemId, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("Could not load eBay listing {}", itemId);
            return mapper.createObjectNode();
        }
    }

    private static void addSummaries(ArrayNode out, Set<String> seen, JsonNode search) {
        JsonNode items = search.path("itemSummaries");
        if (!items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            String id = item.path("itemId").asText("");
            if (!id.isBlank() && seen.add(id)) {
                out.add(item);
            }
        }
    }

    public String buyerPostalCode() {
        if (settings == null) {
            return "";
        }
        return settings.findById(EbayBrowseContext.BUYER_POSTAL_CODE_KEY)
                .map(AppSetting::getValue)
                .map(EbayBrowseContext::normalizePostalCode)
                .orElse("");
    }

    private JsonNode browseGetOrEmpty(String pathAndQuery) {
        try {
            return browseGet(pathAndQuery);
        } catch (Exception e) {
            log.warn("eBay catalog search failed for {}", pathAndQuery, e);
            return mapper.createObjectNode();
        }
    }

    private JsonNode browseGet(String pathAndQuery) {
        return browseUri(URI.create(config.browseApiHost() + pathAndQuery));
    }

    private JsonNode browseUri(URI uri) {
        try {
            String raw = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + tokens.browseAccessToken())
                    .header("X-EBAY-C-MARKETPLACE-ID", config.getMarketplaceId())
                    .header("X-EBAY-C-ENDUSERCTX", EbayBrowseContext.endUserContext(buyerPostalCode()))
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay search failed: " + e.getStatusCode());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse eBay Browse response");
        }
    }

    private static String skuPath(String sku) {
        return URLEncoder.encode(sku, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode sell(String method, String path, String json) {
        URI uri = URI.create(config.apiHost() + path);
        log.info("eBay {} {}", method, path);
        var spec = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(uri)
                .header("Authorization", "Bearer " + tokens.userAccessToken())
                .header("Content-Language", "en-US")
                .header("Accept", "application/json");
        String raw;
        try {
            if (json != null) {
                raw = spec.contentType(MediaType.APPLICATION_JSON).body(json).retrieve().body(String.class);
            } else {
                raw = spec.retrieve().body(String.class);
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("eBay {} {} failed: {} {}", method, path, e.getStatusCode(),
                    ebayError(e.getResponseBodyAsString(), e.getStatusText()));
            if (e.getStatusCode().value() == 404) {
                throw ApiException.notFound("eBay resource not found");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay error: " + ebayError(e.getResponseBodyAsString(), e.getStatusText()));
        }
        if (raw == null || raw.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse eBay Sell response");
        }
    }

    private String hostImageViaMediaApi(byte[] bytes, String filename, String contentType) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return safeFilename(filename);
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(safeContentType(contentType)));
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("image", new HttpEntity<>(resource, fileHeaders));
        try {
            String raw = restClient.post()
                    .uri(mediaHost() + "/commerce/media/v1_beta/image/create_image_from_file")
                    .header("Authorization", "Bearer " + tokens.userAccessToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(raw == null ? "{}" : raw);
            String url = node.path("imageUrl").asText(null);
            if (url == null || url.isBlank()) {
                throw ApiException.unavailable("eBay Media API did not return an image URL");
            }
            return url;
        } catch (ApiException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay image upload failed: "
                    + ebayError(e.getResponseBodyAsString(), e.getStatusText()));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay image upload failed");
        }
    }

    private String hostImageViaTradingApi(byte[] bytes, String filename, String contentType) {
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <UploadSiteHostedPicturesRequest xmlns="urn:ebay:apis:eBLBaseComponents">
                  <ErrorLanguage>en_US</ErrorLanguage>
                  <PictureSet>Supersize</PictureSet>
                </UploadSiteHostedPicturesRequest>
                """;
        HttpHeaders xmlHeaders = new HttpHeaders();
        xmlHeaders.setContentType(MediaType.APPLICATION_XML);
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(safeContentType(contentType)));
        ByteArrayResource image = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return safeFilename(filename);
            }
        };
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("XML Payload", new HttpEntity<>(xml, xmlHeaders));
        form.add("file", new HttpEntity<>(image, imageHeaders));
        try {
            String raw = restClient.post()
                    .uri(tradingHost())
                    .header("X-EBAY-API-CALL-NAME", "UploadSiteHostedPictures")
                    .header("X-EBAY-API-SITEID", "0")
                    .header("X-EBAY-API-COMPATIBILITY-LEVEL", "1399")
                    .header("X-EBAY-API-IAF-TOKEN", tokens.userAccessToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return pictureUrlFromTradingXml(raw);
        } catch (ApiException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay picture upload failed: "
                    + ebayError(e.getResponseBodyAsString(), e.getStatusText()));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay picture upload failed");
        }
    }

    static String pictureUrlFromTradingXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay picture upload returned an empty response");
        }
        if (xml.contains("<Ack>Failure</Ack>") || xml.contains("<Ack>PartialFailure</Ack>")) {
            Matcher message = Pattern.compile("<(?:LongMessage|ShortMessage)>([^<]+)</(?:LongMessage|ShortMessage)>").matcher(xml);
            String detail = message.find() ? message.group(1) : xml;
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay picture upload failed: " + detail);
        }
        Matcher url = Pattern.compile("<FullURL>([^<]+)</FullURL>").matcher(xml);
        if (url.find()) {
            return url.group(1).trim();
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay picture upload did not return an image URL");
    }

    private String ebayError(String raw, String fallback) {
        try {
            JsonNode errors = mapper.readTree(raw == null ? "{}" : raw).path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                StringBuilder out = new StringBuilder();
                for (JsonNode err : errors) {
                    if (!out.isEmpty()) {
                        out.append("; ");
                    }
                    String id = err.path("errorId").asText("");
                    if (!id.isBlank()) {
                        out.append('[').append(id).append("] ");
                    }
                    String longMessage = err.path("longMessage").asText("");
                    String message = err.path("message").asText("");
                    out.append(!longMessage.isBlank() ? longMessage : message);
                }
                if (!out.isEmpty()) {
                    return out.toString();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the raw body or HTTP status text.
        }
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        return fallback == null || fallback.isBlank() ? "unknown error" : fallback;
    }

    private String mediaHost() {
        return "SANDBOX".equalsIgnoreCase(config.getEnv()) ? "https://apim.sandbox.ebay.com" : "https://apim.ebay.com";
    }

    private String tradingHost() {
        return "SANDBOX".equalsIgnoreCase(config.getEnv())
                ? "https://api.sandbox.ebay.com/ws/api.dll"
                : "https://api.ebay.com/ws/api.dll";
    }

    private static boolean hasPositiveDimension(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo.jpg";
        }
        return filename.replace("\\", "_").replace("/", "_");
    }

    private static String safeContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || !contentType.startsWith("image/")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return contentType;
    }

    private static BigDecimal pounds(int lbs, int oz) {
        return BigDecimal.valueOf(lbs).add(BigDecimal.valueOf(oz).divide(BigDecimal.valueOf(16), 3, RoundingMode.HALF_UP));
    }
}
