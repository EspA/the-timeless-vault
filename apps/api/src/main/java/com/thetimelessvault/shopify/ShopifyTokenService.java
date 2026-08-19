package com.thetimelessvault.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Service
public class ShopifyTokenService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyTokenService.class);
    private static final long EXPIRY_SKEW_SECONDS = 60;
    private static final long DEFAULT_EXPIRES_IN = 86400;

    private final AppProperties.Shopify config;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant expiry = Instant.EPOCH;

    @Autowired
    public ShopifyTokenService(AppProperties properties, ObjectMapper mapper) {
        this(properties, mapper, RestClient.builder().build());
    }

    ShopifyTokenService(AppProperties properties, ObjectMapper mapper, RestClient restClient) {
        this.config = properties.getShopify();
        this.mapper = mapper;
        this.restClient = restClient;
    }

    public boolean refreshable() {
        return config.clientCredentialsConfigured();
    }

    public String accessToken() {
        if (hasFreshCache()) {
            return cachedToken;
        }
        if (refreshable()) {
            synchronized (this) {
                if (hasFreshCache()) {
                    return cachedToken;
                }
                refresh();
                return cachedToken;
            }
        }
        if (config.getAdminToken() != null && !config.getAdminToken().isBlank()) {
            return config.getAdminToken().trim();
        }
        throw ApiException.unavailable("Shopify is not configured");
    }

    public void invalidate() {
        cachedToken = null;
        expiry = Instant.EPOCH;
    }

    private boolean hasFreshCache() {
        return cachedToken != null && Instant.now().isBefore(expiry.minusSeconds(EXPIRY_SKEW_SECONDS));
    }

    private void refresh() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        String url = "https://" + config.shopHost() + "/admin/oauth/access_token";
        try {
            String raw = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode token = mapper.readTree(raw == null ? "{}" : raw);
            String accessToken = token.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Shopify did not return an access token");
            }
            long expiresIn = token.path("expires_in").asLong(DEFAULT_EXPIRES_IN);
            cachedToken = accessToken;
            expiry = Instant.now().plusSeconds(expiresIn);
            log.info("Refreshed Shopify Admin token (expires in {}s)", expiresIn);
        } catch (ApiException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Shopify token request failed: " + oauthErrorMessage(e.getResponseBodyAsString(),
                            e.getStatusCode() + " " + e.getStatusText()));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse Shopify token response");
        }
    }

    static String oauthErrorMessage(String raw, String fallback) {
        try {
            JsonNode node = new ObjectMapper().readTree(raw == null ? "{}" : raw);
            String error = node.path("error").asText("");
            String description = node.path("error_description").asText("");
            if (!error.isBlank() && !description.isBlank()) {
                return error + " — " + description;
            }
            if (!description.isBlank()) {
                return description;
            }
            if (!error.isBlank()) {
                return error;
            }
        } catch (Exception ignored) {
            // Fall through to the raw body or HTTP status text.
        }
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        return fallback == null || fallback.isBlank() ? "unknown error" : fallback;
    }
}
