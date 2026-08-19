package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class EbayTokenService {

    private static final Logger log = LoggerFactory.getLogger(EbayTokenService.class);

    public static final String REFRESH_TOKEN_KEY = "ebay.refresh_token";
    static final String STORE_SCOPE = "https://api.ebay.com/oauth/api_scope/sell.stores";
    static final String STORE_READONLY_SCOPE = "https://api.ebay.com/oauth/api_scope/sell.stores.readonly";

    private final AppProperties.Ebay config;
    private final AppSettingRepository settings;
    private final ObjectMapper mapper;
    private final RestClient restClient = RestClient.builder().build();

    private volatile String userAccessToken;
    private volatile Instant userAccessExpiry = Instant.EPOCH;
    private volatile String appAccessToken;
    private volatile Instant appAccessExpiry = Instant.EPOCH;
    private volatile String browseAccessToken;
    private volatile Instant browseAccessExpiry = Instant.EPOCH;

    public EbayTokenService(AppProperties properties, AppSettingRepository settings, ObjectMapper mapper) {
        this.config = properties.getEbay();
        this.settings = settings;
        this.mapper = mapper;
    }

    public String userAccessToken() {
        if (userAccessToken != null && Instant.now().isBefore(userAccessExpiry.minusSeconds(60))) {
            return userAccessToken;
        }
        String refresh = refreshToken();
        if (refresh == null || refresh.isBlank()) {
            throw ApiException.unavailable("eBay refresh token is missing. Complete OAuth on the Settings page.");
        }
        JsonNode token = refreshUserAccessToken(refresh);
        applyUserToken(token);
        return userAccessToken;
    }

    public String appAccessToken() {
        if (appAccessToken != null && Instant.now().isBefore(appAccessExpiry.minusSeconds(60))) {
            return appAccessToken;
        }
        JsonNode token = exchange(config.apiHost(), config.getClientId(), config.getClientSecret(),
                "client_credentials", null, "https://api.ebay.com/oauth/api_scope");
        appAccessToken = token.path("access_token").asText();
        appAccessExpiry = Instant.now().plusSeconds(token.path("expires_in").asLong(7200));
        return appAccessToken;
    }

    public String browseAccessToken() {
        if (browseAccessToken != null && Instant.now().isBefore(browseAccessExpiry.minusSeconds(60))) {
            return browseAccessToken;
        }
        if (!config.browseConfigured()) {
            throw ApiException.unavailable("eBay client credentials are not configured");
        }
        JsonNode token = exchange(config.browseApiHost(), config.browseClientId(), config.browseClientSecret(),
                "client_credentials", null, "https://api.ebay.com/oauth/api_scope");
        browseAccessToken = token.path("access_token").asText();
        browseAccessExpiry = Instant.now().plusSeconds(token.path("expires_in").asLong(7200));
        return browseAccessToken;
    }

    public String authorizationUrl(String state) {
        return config.authHost() + "/oauth2/authorize?client_id=" + url(config.getClientId())
                + "&redirect_uri=" + url(config.getRuName())
                + "&response_type=code&scope=" + url(sellScopes())
                + "&state=" + url(state);
    }

    public void exchangeAuthorizationCode(String code) {
        JsonNode token = exchange("authorization_code", authorizationCodeFrom(code), sellScopes());
        String refresh = token.path("refresh_token").asText(null);
        if (refresh == null || refresh.isBlank()) {
            throw ApiException.unavailable(
                    "eBay did not return a refresh token. Use Connect eBay (OAuth consent), not Get a User Token Here."
            );
        }
        settings.save(new AppSetting(REFRESH_TOKEN_KEY, refresh));
        applyUserToken(token);
        logGrantedScopes("authorization_code", token);
    }

    static String authorizationCodeFrom(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Paste the eBay success URL or the code= value from it.");
        }
        String value = raw.trim();
        int codeIdx = value.toLowerCase().indexOf("code=");
        if (codeIdx >= 0) {
            value = value.substring(codeIdx + 5);
            int amp = value.indexOf('&');
            if (amp >= 0) {
                value = value.substring(0, amp);
            }
        }
        if (value.indexOf('%') >= 0) {
            value = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        if (value.isBlank()) {
            throw ApiException.badRequest("Paste the eBay success URL or the code= value from it.");
        }
        return value.trim();
    }

    public boolean hasRefreshToken() {
        return refreshToken() != null && !refreshToken().isBlank();
    }

    private String refreshToken() {
        return settings.findById(REFRESH_TOKEN_KEY)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(config.getRefreshToken());
    }

    private JsonNode refreshUserAccessToken(String refresh) {
        try {
            JsonNode token = exchange("refresh_token", refresh, sellScopes());
            logGrantedScopes("refresh_token", token);
            return token;
        } catch (ApiException e) {
            if (!isInvalidScope(e.getMessage())) {
                throw e;
            }
        }
        try {
            JsonNode token = exchange("refresh_token", refresh, legacySellScopes());
            log.warn("eBay refresh token does not include sell.stores.readonly. Using sell.stores.");
            logGrantedScopes("refresh_token", token);
            return token;
        } catch (ApiException e) {
            if (!isInvalidScope(e.getMessage())) {
                throw e;
            }
        }
        log.warn("eBay refresh token was granted without store scopes. Developer-portal grant types are not applied until you reconnect eBay on Settings.");
        return exchange("refresh_token", refresh, null);
    }

    private void applyUserToken(JsonNode token) {
        userAccessToken = token.path("access_token").asText();
        userAccessExpiry = Instant.now().plusSeconds(token.path("expires_in").asLong(7200));
    }

    private void logGrantedScopes(String grant, JsonNode token) {
        String scope = token.path("scope").asText("");
        if (scope.isBlank()) {
            return;
        }
        if (hasStoreScope(scope)) {
            log.info("eBay {} token includes store scopes", grant);
        } else {
            log.warn("eBay {} token scopes do not include sell.stores: {}", grant, scope);
        }
    }

    private JsonNode exchange(String grantType, String secret, String scope) {
        if (!config.configured()) {
            throw ApiException.unavailable("eBay client credentials are not configured");
        }
        return exchange(config.apiHost(), config.getClientId(), config.getClientSecret(), grantType, secret, scope);
    }

    private JsonNode exchange(String host, String clientId, String clientSecret, String grantType, String secret, String scope) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }
        if ("refresh_token".equals(grantType)) {
            form.add("refresh_token", secret);
        } else if ("authorization_code".equals(grantType)) {
            form.add("code", secret);
            form.add("redirect_uri", config.getRuName());
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        try {
            String raw = restClient.post()
                    .uri(host + "/identity/v1/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", "Basic " + basic)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(raw);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String detail = oauthErrorMessage(e.getResponseBodyAsString(), e.getStatusCode() + " " + e.getStatusText());
            String hint = "";
            if (detail.toLowerCase().contains("invalid_grant") || detail.toLowerCase().contains("invalid refresh")) {
                hint = " Connect eBay on the Settings page to generate a new refresh token.";
            } else if (detail.toLowerCase().contains("invalid_scope")) {
                hint = " Reconnect eBay on the Settings page so the token includes inventory, account, and store scopes.";
            } else if (host.contains("api.ebay.com") && "SANDBOX".equalsIgnoreCase(config.getEnv())) {
                hint = " Market watch searches live eBay.com and needs production App ID keys (EBAY_BROWSE_CLIENT_ID / EBAY_BROWSE_CLIENT_SECRET). Sandbox has no live LEGO listings.";
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay token request failed: " + detail + "." + hint);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse eBay token response");
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

    static String sellScopes() {
        return legacySellScopes() + " " + STORE_READONLY_SCOPE;
    }

    static String legacySellScopes() {
        return String.join(" ",
                "https://api.ebay.com/oauth/api_scope/sell.inventory",
                "https://api.ebay.com/oauth/api_scope/sell.account",
                "https://api.ebay.com/oauth/api_scope/sell.fulfillment",
                STORE_SCOPE
        );
    }

    static boolean hasStoreScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return false;
        }
        return scope.contains(STORE_SCOPE);
    }

    static boolean isInvalidScope(String message) {
        return message != null && message.toLowerCase().contains("invalid_scope");
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
