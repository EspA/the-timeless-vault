package com.thetimelessvault.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inbound.ShippingCarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CarrierTrackingClient {

    private static final Logger log = LoggerFactory.getLogger(CarrierTrackingClient.class);

    private final AppProperties properties;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final ConcurrentHashMap<String, CachedToken> tokens = new ConcurrentHashMap<>();

    @Autowired
    public CarrierTrackingClient(AppProperties properties, ObjectMapper mapper) {
        this(properties, mapper, RestClient.builder().build());
    }

    CarrierTrackingClient(AppProperties properties, ObjectMapper mapper, RestClient restClient) {
        this.properties = properties;
        this.mapper = mapper;
        this.restClient = restClient;
    }

    public boolean isDelivered(ShippingCarrier carrier, String trackingNumber) {
        if (carrier == null || trackingNumber == null || trackingNumber.isBlank()) {
            return false;
        }
        String tracking = trackingNumber.trim();
        try {
            return switch (carrier) {
                case UPS -> upsDelivered(tracking);
                case USPS -> uspsDelivered(tracking);
                case FEDEX -> fedexDelivered(tracking);
                default -> false;
            };
        } catch (RuntimeException e) {
            log.warn("Could not track {} {}: {}", carrier, tracking, e.getMessage());
            return false;
        }
    }

    private boolean upsDelivered(String tracking) {
        AppProperties.OAuthApi config = properties.getUps();
        if (!config.configured()) {
            return false;
        }
        String token = token("ups", () -> upsToken(config));
        JsonNode body = getJson(
                config.host() + "/api/track/v1/details/" + tracking + "?locale=en_US",
                Map.of(
                        "Authorization", "Bearer " + token,
                        "transId", UUID.randomUUID().toString(),
                        "transactionSrc", "TheTimelessVault"
                )
        );
        return upsLooksDelivered(body);
    }

    private boolean uspsDelivered(String tracking) {
        AppProperties.OAuthApi config = properties.getUsps();
        if (!config.configured()) {
            return false;
        }
        String token = token("usps", () -> uspsToken(config));
        JsonNode body = getJson(
                config.host() + "/tracking/v3/tracking/" + tracking + "?expand=DETAIL",
                Map.of("Authorization", "Bearer " + token)
        );
        return uspsLooksDelivered(body);
    }

    private boolean fedexDelivered(String tracking) {
        AppProperties.OAuthApi config = properties.getFedex();
        if (!config.configured()) {
            return false;
        }
        String token = token("fedex", () -> fedexToken(config));
        JsonNode body = postJson(
                config.host() + "/track/v1/trackingnumbers",
                Map.of(
                        "Authorization", "Bearer " + token,
                        "x-locale", "en_US",
                        "x-customer-transaction-id", UUID.randomUUID().toString()
                ),
                Map.of(
                        "includeDetailedScans", false,
                        "trackingInfo", java.util.List.of(Map.of("trackingNumberInfo", Map.of("trackingNumber", tracking)))
                )
        );
        return fedexLooksDelivered(body);
    }

    static boolean upsLooksDelivered(JsonNode body) {
        if (body == null || body.isMissingNode()) {
            return false;
        }
        JsonNode shipments = body.path("trackResponse").path("shipment");
        if (!shipments.isArray()) {
            return mentionsDelivered(body.toString());
        }
        for (JsonNode shipment : shipments) {
            if (statusLooksDelivered(shipment.path("currentStatus"))) {
                return true;
            }
            JsonNode packages = shipment.path("package");
            if (packages.isArray()) {
                for (JsonNode pkg : packages) {
                    if (statusLooksDelivered(pkg.path("currentStatus"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean uspsLooksDelivered(JsonNode body) {
        if (body == null || body.isMissingNode()) {
            return false;
        }
        return statusLooksDelivered(body.path("statusCategory"))
                || statusLooksDelivered(body.path("status"))
                || statusLooksDelivered(body.path("statusSummary"));
    }

    static boolean fedexLooksDelivered(JsonNode body) {
        if (body == null || body.isMissingNode()) {
            return false;
        }
        JsonNode results = body.path("output").path("completeTrackResults");
        if (!results.isArray()) {
            return mentionsDelivered(body.toString());
        }
        for (JsonNode complete : results) {
            JsonNode tracks = complete.path("trackResults");
            if (!tracks.isArray()) {
                continue;
            }
            for (JsonNode track : tracks) {
                JsonNode latest = track.path("latestStatusDetail");
                if (statusLooksDelivered(latest.path("code"))
                        || statusLooksDelivered(latest.path("derivedCode"))
                        || statusLooksDelivered(latest.path("statusByLocale"))
                        || statusLooksDelivered(latest.path("description"))) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean statusLooksDelivered(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        String value = node.isValueNode() ? node.asText("") : node.path("code").asText("")
                + " " + node.path("description").asText("")
                + " " + node.path("type").asText("")
                + " " + node.path("statusCode").asText("");
        return mentionsDelivered(value);
    }

    static boolean mentionsDelivered(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("delivered")
                || normalized.equals("dl")
                || normalized.contains(" 011")
                || normalized.startsWith("011")
                || normalized.equals("011");
    }

    private CachedToken upsToken(AppProperties.OAuthApi config) {
        String basic = Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        JsonNode body = restClient.post()
                .uri(config.host() + "/security/v1/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic " + basic)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(JsonNode.class);
        return cached(body);
    }

    private CachedToken uspsToken(AppProperties.OAuthApi config) {
        JsonNode body = restClient.post()
                .uri(config.host() + "/oauth2/v3/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "client_id", config.getClientId(),
                        "client_secret", config.getClientSecret(),
                        "grant_type", "client_credentials",
                        "scope", "tracking"
                ))
                .retrieve()
                .body(JsonNode.class);
        return cached(body);
    }

    private CachedToken fedexToken(AppProperties.OAuthApi config) {
        JsonNode body = restClient.post()
                .uri(config.host() + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=" + config.getClientId()
                        + "&client_secret=" + config.getClientSecret())
                .retrieve()
                .body(JsonNode.class);
        return cached(body);
    }

    private String token(String key, java.util.function.Supplier<CachedToken> fetch) {
        CachedToken current = tokens.get(key);
        if (current != null && Instant.now().isBefore(current.expiry().minusSeconds(60))) {
            return current.value();
        }
        CachedToken next = fetch.get();
        tokens.put(key, next);
        return next.value();
    }

    private CachedToken cached(JsonNode body) {
        if (body == null || body.path("access_token").asText("").isBlank()) {
            throw new IllegalStateException("Carrier OAuth token was empty");
        }
        long seconds = body.path("expires_in").asLong(3300);
        return new CachedToken(body.path("access_token").asText(), Instant.now().plusSeconds(seconds));
    }

    private JsonNode getJson(String uri, Map<String, String> headers) {
        var spec = restClient.get().uri(uri);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            spec = spec.header(header.getKey(), header.getValue());
        }
        try {
            JsonNode body = spec.retrieve().body(JsonNode.class);
            return body == null ? mapper.nullNode() : body;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    private JsonNode postJson(String uri, Map<String, String> headers, Object payload) {
        var spec = restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            spec = spec.header(header.getKey(), header.getValue());
        }
        try {
            JsonNode body = spec.body(payload).retrieve().body(JsonNode.class);
            return body == null ? mapper.nullNode() : body;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    private record CachedToken(String value, Instant expiry) {
    }
}
