package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.ChannelPrice;
import com.thetimelessvault.inventory.InventoryItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ShopifyClient {

    private final AppProperties.Shopify config;
    private final ObjectMapper mapper;
    private final ShopifyTokenService tokens;
    private final RestClient restClient;

    @Autowired
    public ShopifyClient(AppProperties properties, ObjectMapper mapper, ShopifyTokenService tokens) {
        this(properties, mapper, tokens, RestClient.builder().build());
    }

    ShopifyClient(AppProperties properties, ObjectMapper mapper, ShopifyTokenService tokens, RestClient restClient) {
        this.config = properties.getShopify();
        this.mapper = mapper;
        this.tokens = tokens;
        this.restClient = restClient;
    }

    public boolean configured() {
        return config.configured();
    }

    public Map<String, String> shipFromAddress() {
        String name = config.getLocationName() == null || config.getLocationName().isBlank()
                ? "Private Mail Box"
                : config.getLocationName().trim();
        Map<String, String> fallback = new java.util.LinkedHashMap<>();
        fallback.put("name", name);
        fallback.put("addressLine1", "");
        fallback.put("city", "");
        fallback.put("stateOrProvince", "");
        fallback.put("postalCode", "");
        fallback.put("country", "US");
        if (!configured()) {
            return fallback;
        }
        try {
            JsonNode data = graphql("""
                    query {
                      locations(first: 50) {
                        nodes {
                          name
                          isActive
                          address { address1 city provinceCode zip countryCodeV2 }
                        }
                      }
                    }
                    """, mapper.createObjectNode());
            JsonNode match = null;
            for (JsonNode node : data.path("locations").path("nodes")) {
                if (name.equalsIgnoreCase(node.path("name").asText(""))) {
                    match = node;
                    break;
                }
            }
            if (match == null) {
                return fallback;
            }
            JsonNode address = match.path("address");
            fallback.put("addressLine1", address.path("address1").asText(""));
            fallback.put("city", address.path("city").asText(""));
            fallback.put("stateOrProvince", address.path("provinceCode").asText(""));
            fallback.put("postalCode", address.path("zip").asText(""));
            String country = address.path("countryCodeV2").asText("");
            fallback.put("country", country.isBlank() ? "US" : country);
            return fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public List<Map<String, String>> collections() {
        try {
            List<Map<String, String>> admin = adminCollections();
            if (!admin.isEmpty()) {
                return admin;
            }
        } catch (ApiException ignored) {
            // Client-credentials tokens often lack read_products. Published collections are public.
        }
        return storefrontCollections();
    }

    private List<Map<String, String>> adminCollections() {
        JsonNode data = graphql("""
                query {
                  collections(first: 100, sortKey: TITLE) {
                    nodes { id title }
                  }
                }
                """, mapper.createObjectNode());
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonNode node : data.path("collections").path("nodes")) {
            result.add(Map.of("id", node.path("id").asText(), "title", node.path("title").asText()));
        }
        return result;
    }

    private List<Map<String, String>> storefrontCollections() {
        if (config.shopHost().isBlank()) {
            return List.of();
        }
        String url = "https://" + config.shopHost() + "/collections.json?limit=250";
        try {
            String raw = restClient.get()
                    .uri(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "TheTimelessVault/1.0")
                    .retrieve()
                    .body(String.class);
            JsonNode root = mapper.readTree(raw == null ? "{}" : raw);
            List<Map<String, String>> result = new ArrayList<>();
            for (JsonNode node : root.path("collections")) {
                long id = node.path("id").asLong(0);
                String title = node.path("title").asText("");
                if (id == 0 || title.isBlank()) {
                    continue;
                }
                result.add(Map.of(
                        "id", "gid://shopify/Collection/" + id,
                        "title", title
                ));
            }
            result.sort(Comparator.comparing(item -> item.get("title"), String.CASE_INSENSITIVE_ORDER));
            return result;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not load Shopify collections");
        }
    }

    public String resolveConstructionCategoryId() {
        if (config.getCategoryId() != null && !config.getCategoryId().isBlank()) {
            return config.getCategoryId();
        }
        JsonNode data = graphql("""
                query {
                  taxonomy {
                    categories(first: 25, search: "Construction Set Toys") {
                      nodes { id name fullName }
                    }
                  }
                }
                """, mapper.createObjectNode());
        for (JsonNode node : data.path("taxonomy").path("categories").path("nodes")) {
            String name = node.path("name").asText("");
            if (name.equalsIgnoreCase("Construction Set Toys") || node.path("fullName").asText("").contains("Construction Set Toys")) {
                return node.path("id").asText();
            }
        }
        return null;
    }

    public JsonNode createProduct(InventoryItem item, List<String> photoUrls, String categoryId) {
        ObjectNode product = mapper.createObjectNode();
        product.put("title", item.getTitle());
        product.put("descriptionHtml", com.thetimelessvault.common.DescriptionHtml.forShopify(item.getDescription()));
        product.put("productType", item.getItemType() == com.thetimelessvault.common.ItemType.POLYBAG ? "Polybag" : "Set");
        product.put("vendor", "The Timeless Vault");
        product.put("status", "UNLISTED");
        if (categoryId != null && !categoryId.isBlank()) {
            product.put("category", categoryId);
        }
        if (item.getShopifyCollectionIds() != null && !item.getShopifyCollectionIds().isBlank()) {
            ArrayNode collections = product.putArray("collectionsToJoin");
            for (String id : item.getShopifyCollectionIds().split(",")) {
                if (!id.isBlank()) {
                    collections.add(id.trim());
                }
            }
        }

        ArrayNode media = mapper.createArrayNode();
        for (String url : photoUrls) {
            ObjectNode mediaItem = mapper.createObjectNode();
            mediaItem.put("originalSource", url);
            mediaItem.put("mediaContentType", "IMAGE");
            mediaItem.put("alt", item.getTitle());
            media.add(mediaItem);
        }

        ObjectNode variables = mapper.createObjectNode();
        variables.set("product", product);
        variables.set("media", media);

        JsonNode data = graphql("""
                mutation productCreate($product: ProductCreateInput!, $media: [CreateMediaInput!]) {
                  productCreate(product: $product, media: $media) {
                    product {
                      id
                      handle
                      status
                      onlineStoreUrl
                      variants(first: 1) {
                        nodes {
                          id
                          inventoryItem { id }
                        }
                      }
                    }
                    userErrors { field message }
                  }
                }
                """, variables);
        JsonNode payload = data.path("productCreate");
        assertNoUserErrors(payload);
        JsonNode created = payload.path("product");
        String productId = created.path("id").asText(null);
        String variantId = created.path("variants").path("nodes").path(0).path("id").asText(null);
        String inventoryItemId = created.path("variants").path("nodes").path(0).path("inventoryItem").path("id").asText(null);
        if (productId != null && variantId != null) {
            setVariantDetails(productId, variantId, item);
        }
        if (inventoryItemId == null || inventoryItemId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify did not return an inventory item for the product");
        }
        setOnHandQuantity(inventoryItemId, Math.max(item.getQuantity(), 0));
        if (productId != null && !productId.isBlank()) {
            publishToAllChannels(productId);
        }
        return created;
    }

    public JsonNode updateProduct(String productId, InventoryItem item, List<String> photoUrls) {
        if (productId == null || productId.isBlank()) {
            throw ApiException.badRequest("Shopify product id is missing");
        }
        JsonNode current = getProduct(productId);
        ObjectNode product = mapper.createObjectNode();
        product.put("id", productId);
        product.put("title", item.getTitle());
        product.put("descriptionHtml", com.thetimelessvault.common.DescriptionHtml.forShopify(item.getDescription()));
        product.put("productType", item.getItemType() == com.thetimelessvault.common.ItemType.POLYBAG ? "Polybag" : "Set");
        String categoryId = resolveConstructionCategoryId();
        if (categoryId != null && !categoryId.isBlank()) {
            product.put("category", categoryId);
        }
        if (item.getShopifyCollectionIds() != null && !item.getShopifyCollectionIds().isBlank()) {
            ArrayNode collections = product.putArray("collectionsToJoin");
            for (String id : item.getShopifyCollectionIds().split(",")) {
                if (!id.isBlank()) {
                    collections.add(id.trim());
                }
            }
        }
        ObjectNode variables = mapper.createObjectNode();
        variables.set("product", product);
        JsonNode data = graphql("""
                mutation productUpdate($product: ProductUpdateInput!) {
                  productUpdate(product: $product) {
                    product {
                      id
                      handle
                      status
                      onlineStoreUrl
                      variants(first: 1) {
                        nodes {
                          id
                          inventoryItem { id }
                        }
                      }
                    }
                    userErrors { field message }
                  }
                }
                """, variables);
        JsonNode payload = data.path("productUpdate");
        assertNoUserErrors(payload);
        JsonNode updated = payload.path("product");
        String variantId = firstVariantId(updated, current);
        String inventoryItemId = firstInventoryItemId(updated, current);
        if (variantId == null || variantId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify did not return a variant for the product");
        }
        setVariantDetails(productId, variantId, item);
        if (inventoryItemId == null || inventoryItemId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify did not return an inventory item for the product");
        }
        setOnHandQuantity(inventoryItemId, Math.max(item.getQuantity(), 0));
        replaceMedia(productId, current, photoUrls, item.getTitle());
        return updated;
    }

    private JsonNode getProduct(String productId) {
        ObjectNode variables = mapper.createObjectNode();
        variables.put("id", productId);
        JsonNode product = graphql("""
                query product($id: ID!) {
                  product(id: $id) {
                    id
                    handle
                    status
                    onlineStoreUrl
                    media(first: 250) {
                      nodes {
                        id
                        ... on MediaImage { id }
                      }
                    }
                    variants(first: 1) {
                      nodes {
                        id
                        inventoryItem { id }
                      }
                    }
                  }
                }
                """, variables).path("product");
        if (product.isMissingNode() || product.path("id").asText("").isBlank()) {
            throw ApiException.notFound("Shopify product not found");
        }
        return product;
    }

    private static String firstVariantId(JsonNode... products) {
        for (JsonNode product : products) {
            String id = product.path("variants").path("nodes").path(0).path("id").asText(null);
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return null;
    }

    private static String firstInventoryItemId(JsonNode... products) {
        for (JsonNode product : products) {
            String id = product.path("variants").path("nodes").path(0).path("inventoryItem").path("id").asText(null);
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return null;
    }

    private void replaceMedia(String productId, JsonNode current, List<String> photoUrls, String alt) {
        List<String> existingIds = collectMediaIds(current);
        List<String> createdIds = List.of();
        if (photoUrls != null && !photoUrls.isEmpty()) {
            createdIds = createMedia(productId, photoUrls, alt);
        }
        List<String> removeIds = mediaIdsToRemove(existingIds, createdIds);
        if (!removeIds.isEmpty()) {
            deleteMedia(productId, removeIds);
        }
    }

    private List<String> createMedia(String productId, List<String> photoUrls, String alt) {
        ObjectNode variables = mapper.createObjectNode();
        variables.put("productId", productId);
        ArrayNode media = variables.putArray("media");
        for (String url : photoUrls) {
            ObjectNode mediaItem = media.addObject();
            mediaItem.put("originalSource", url);
            mediaItem.put("mediaContentType", "IMAGE");
            mediaItem.put("alt", alt);
        }
        JsonNode payload = graphql("""
                mutation productCreateMedia($productId: ID!, $media: [CreateMediaInput!]!) {
                  productCreateMedia(productId: $productId, media: $media) {
                    media { id }
                    userErrors { field message }
                    mediaUserErrors { field message }
                  }
                }
                """, variables).path("productCreateMedia");
        assertNoUserErrors(payload);
        return collectMediaIds(payload);
    }

    private void deleteMedia(String productId, List<String> mediaIds) {
        ObjectNode variables = mapper.createObjectNode();
        variables.put("productId", productId);
        ArrayNode ids = variables.putArray("mediaIds");
        mediaIds.forEach(ids::add);
        JsonNode payload = graphql("""
                mutation productDeleteMedia($productId: ID!, $mediaIds: [ID!]!) {
                  productDeleteMedia(productId: $productId, mediaIds: $mediaIds) {
                    deletedMediaIds
                    deletedProductImageIds
                    userErrors { field message }
                    mediaUserErrors { field message }
                  }
                }
                """, variables).path("productDeleteMedia");
        assertNoUserErrors(payload);
    }

    static List<String> collectMediaIds(JsonNode root) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        addMediaIds(ids, root.path("media"));
        addMediaIds(ids, root.path("nodes"));
        addMediaIds(ids, root);
        return List.copyOf(ids);
    }

    static List<String> mediaIdsToRemove(List<String> existingIds, List<String> createdIds) {
        if (existingIds == null || existingIds.isEmpty()) {
            return List.of();
        }
        if (createdIds == null || createdIds.isEmpty()) {
            return List.copyOf(existingIds);
        }
        Set<String> keep = Set.copyOf(createdIds);
        return existingIds.stream().filter(id -> !keep.contains(id)).toList();
    }

    private static void addMediaIds(Set<String> ids, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                addMediaIds(ids, child);
            }
            return;
        }
        if (node.isObject()) {
            JsonNode nested = node.get("nodes");
            if (nested != null) {
                addMediaIds(ids, nested);
            }
            JsonNode edges = node.get("edges");
            if (edges != null && edges.isArray()) {
                for (JsonNode edge : edges) {
                    addMediaIds(ids, edge.path("node"));
                }
            }
            String id = node.path("id").asText("");
            if (isProductMediaId(id)) {
                ids.add(id);
            }
        }
    }

    private static boolean isProductMediaId(String id) {
        return id != null && !id.isBlank()
                && !id.contains("gid://shopify/Product/")
                && !id.contains("gid://shopify/ProductVariant/")
                && !id.contains("gid://shopify/InventoryItem/")
                && !id.contains("gid://shopify/Collection/");
    }

    public String updateProductStatus(String productId, String status) {
        ObjectNode product = mapper.createObjectNode();
        product.put("id", productId);
        product.put("status", status);
        ObjectNode variables = mapper.createObjectNode();
        variables.set("product", product);
        JsonNode data = graphql("""
                mutation productUpdate($product: ProductUpdateInput!) {
                  productUpdate(product: $product) {
                    product { id status }
                    userErrors { field message }
                  }
                }
                """, variables);
        JsonNode payload = data.path("productUpdate");
        assertNoUserErrors(payload);
        String updated = payload.path("product").path("status").asText(null);
        if (updated == null || updated.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify did not return a product status");
        }
        return updated;
    }

    public void deleteProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return;
        }
        ObjectNode input = mapper.createObjectNode();
        input.put("id", productId);
        ObjectNode variables = mapper.createObjectNode();
        variables.set("input", input);
        JsonNode data = graphql("""
                mutation productDelete($input: ProductDeleteInput!) {
                  productDelete(input: $input) {
                    deletedProductId
                    userErrors { field message }
                  }
                }
                """, variables);
        JsonNode payload = data.path("productDelete");
        JsonNode errors = payload.path("userErrors");
        if (errors.isArray() && !errors.isEmpty()) {
            String message = errors.toString().toLowerCase(Locale.ROOT);
            if (message.contains("not found") || message.contains("does not exist")) {
                return;
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify user error: " + errors);
        }
    }

    private void setVariantDetails(String productId, String variantId, InventoryItem item) {
        ObjectNode variables = mapper.createObjectNode();
        variables.put("productId", productId);
        ArrayNode variants = variables.putArray("variants");
        ObjectNode variant = variants.addObject();
        variant.put("id", variantId);
        variant.put("price", ChannelPrice.required(item, Platform.SHOPIFY));
        ObjectNode inventoryItem = variant.putObject("inventoryItem");
        inventoryItem.put("tracked", true);
        if (item.getSku() != null && !item.getSku().isBlank()) {
            inventoryItem.put("sku", item.getSku());
        }
        double pounds = item.getPackageLbs() + (item.getPackageOz() / 16.0);
        if (pounds > 0) {
            ObjectNode weight = inventoryItem.putObject("measurement").putObject("weight");
            weight.put("value", pounds);
            weight.put("unit", "POUNDS");
        }
        JsonNode data = graphql("""
                mutation productVariantsBulkUpdate($productId: ID!, $variants: [ProductVariantsBulkInput!]!) {
                  productVariantsBulkUpdate(productId: $productId, variants: $variants) {
                    userErrors { field message }
                  }
                }
                """, variables);
        assertNoUserErrors(data.path("productVariantsBulkUpdate"));
    }

    private void setOnHandQuantity(String inventoryItemId, int quantity) {
        String locationId = resolveLocationId();
        activateAtLocation(inventoryItemId, locationId);
        ObjectNode input = mapper.createObjectNode();
        input.put("name", "on_hand");
        input.put("reason", "correction");
        input.put("ignoreCompareQuantity", true);
        ArrayNode quantities = input.putArray("quantities");
        ObjectNode row = quantities.addObject();
        row.put("inventoryItemId", inventoryItemId);
        row.put("locationId", locationId);
        row.put("quantity", quantity);
        ObjectNode variables = mapper.createObjectNode();
        variables.set("input", input);
        JsonNode data = graphql("""
                mutation inventorySetQuantities($input: InventorySetQuantitiesInput!) {
                  inventorySetQuantities(input: $input) {
                    userErrors { field message }
                  }
                }
                """, variables);
        assertNoUserErrors(data.path("inventorySetQuantities"));
    }

    private void activateAtLocation(String inventoryItemId, String locationId) {
        ObjectNode variables = mapper.createObjectNode();
        variables.put("inventoryItemId", inventoryItemId);
        ArrayNode updates = variables.putArray("inventoryItemUpdates");
        ObjectNode update = updates.addObject();
        update.put("locationId", locationId);
        update.put("activate", true);
        JsonNode data = graphql("""
                mutation inventoryBulkToggleActivation($inventoryItemId: ID!, $inventoryItemUpdates: [InventoryBulkToggleActivationInput!]!) {
                  inventoryBulkToggleActivation(inventoryItemId: $inventoryItemId, inventoryItemUpdates: $inventoryItemUpdates) {
                    userErrors { field message }
                  }
                }
                """, variables);
        JsonNode errors = data.path("inventoryBulkToggleActivation").path("userErrors");
        if (!errors.isArray() || errors.isEmpty()) {
            return;
        }
        for (JsonNode error : errors) {
            String message = error.path("message").asText("").toLowerCase(Locale.ROOT);
            if (!message.contains("already")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify user error: " + errors);
            }
        }
    }

    private String resolveLocationId() {
        String wanted = config.getLocationName() == null || config.getLocationName().isBlank()
                ? "Private Mail Box"
                : config.getLocationName().trim();
        JsonNode data = graphql("""
                query {
                  locations(first: 50) {
                    nodes { id name isActive }
                  }
                }
                """, mapper.createObjectNode());
        JsonNode nodes = data.path("locations").path("nodes");
        for (JsonNode node : nodes) {
            if (wanted.equalsIgnoreCase(node.path("name").asText(""))) {
                return node.path("id").asText();
            }
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Shopify location \"" + wanted + "\" was not found. Create it in Shopify Admin or set SHOPIFY_LOCATION_NAME."
        );
    }

    private void publishToAllChannels(String productId) {
        List<String> publicationIds = listPublicationIds();
        if (publicationIds.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Shopify returned no sales channels. Grant read_publications and write_publications on the custom app."
            );
        }
        if (publishToChannels(productId, publicationIds)) {
            return;
        }
        int published = 0;
        List<String> failures = new ArrayList<>();
        for (String publicationId : publicationIds) {
            if (publishToChannels(productId, List.of(publicationId))) {
                published++;
            } else {
                failures.add(publicationId);
            }
        }
        if (published == 0) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not publish the product to any Shopify sales channel. Grant write_publications on the custom app."
                            + (failures.isEmpty() ? "" : " Failed publications: " + String.join(", ", failures))
            );
        }
    }

    private boolean publishToChannels(String productId, List<String> publicationIds) {
        ObjectNode variables = mapper.createObjectNode();
        variables.put("id", productId);
        ArrayNode input = variables.putArray("input");
        for (String publicationId : publicationIds) {
            input.addObject().put("publicationId", publicationId);
        }
        try {
            JsonNode data = graphql("""
                    mutation publishablePublish($id: ID!, $input: [PublicationInput!]!) {
                      publishablePublish(id: $id, input: $input) {
                        userErrors { field message }
                      }
                    }
                    """, variables);
            JsonNode errors = data.path("publishablePublish").path("userErrors");
            return !errors.isArray() || errors.isEmpty();
        } catch (ApiException e) {
            return false;
        }
    }

    private List<String> listPublicationIds() {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        while (hasNext) {
            ObjectNode variables = mapper.createObjectNode();
            variables.put("first", 50);
            if (cursor != null) {
                variables.put("after", cursor);
            }
            JsonNode connection = graphql("""
                    query publications($first: Int!, $after: String) {
                      publications(first: $first, after: $after) {
                        nodes { id }
                        pageInfo { hasNextPage endCursor }
                      }
                    }
                    """, variables).path("publications");
            for (JsonNode node : connection.path("nodes")) {
                String id = node.path("id").asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            hasNext = connection.path("pageInfo").path("hasNextPage").asBoolean(false);
            cursor = connection.path("pageInfo").path("endCursor").asText(null);
            if (cursor == null || cursor.isBlank()) {
                hasNext = false;
            }
        }
        return ids;
    }

    JsonNode graphql(String query, JsonNode variables) {
        if (!config.configured()) {
            throw ApiException.unavailable("Shopify is not configured");
        }
        try {
            return executeGraphql(query, variables, tokens.accessToken());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 && tokens.refreshable()) {
                tokens.invalidate();
                try {
                    return executeGraphql(query, variables, tokens.accessToken());
                } catch (RestClientResponseException retry) {
                    throw shopifyHttpError(retry);
                }
            }
            throw shopifyHttpError(e);
        }
    }

    private JsonNode executeGraphql(String query, JsonNode variables, String accessToken) {
        ObjectNode body = mapper.createObjectNode();
        body.put("query", query);
        body.set("variables", variables);
        String url = "https://" + config.shopHost() + "/admin/api/" + config.getApiVersion() + "/graphql.json";
        String raw = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Shopify-Access-Token", accessToken)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = mapper.readTree(raw);
            if (root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify error: " + root.get("errors").toString());
            }
            return root.path("data");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse Shopify response");
        }
    }

    private ApiException shopifyHttpError(RestClientResponseException e) {
        if (e.getStatusCode().value() == 401) {
            if (tokens.refreshable()) {
                return new ApiException(HttpStatus.BAD_GATEWAY, "Shopify rejected the Admin token after a refresh.");
            }
            return new ApiException(HttpStatus.BAD_GATEWAY,
                    "Shopify Admin token was rejected. Set SHOPIFY_CLIENT_ID and SHOPIFY_CLIENT_SECRET so it can be refreshed automatically.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY,
                "Shopify request failed: " + e.getStatusCode() + " " + e.getStatusText());
    }

    private static void assertNoUserErrors(JsonNode payload) {
        JsonNode errors = payload.path("userErrors");
        if (errors.isArray() && !errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify user error: " + errors.toString());
        }
        JsonNode mediaErrors = payload.path("mediaUserErrors");
        if (mediaErrors.isArray() && !mediaErrors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify user error: " + mediaErrors.toString());
        }
    }
}
