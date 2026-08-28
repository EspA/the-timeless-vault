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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        return track(carrier, trackingNumber).delivered();
    }

    public Snapshot track(ShippingCarrier carrier, String trackingNumber) {
        if (carrier == null || trackingNumber == null || trackingNumber.isBlank()) {
            return Snapshot.EMPTY;
        }
        String tracking = trackingNumber.trim();
        try {
            return switch (carrier) {
                case UPS -> upsTrack(tracking);
                case USPS -> uspsTrack(tracking);
                case FEDEX -> fedexTrack(tracking);
                default -> Snapshot.EMPTY;
            };
        } catch (RuntimeException e) {
            log.warn("Could not track {} {}: {}", carrier, tracking, e.getMessage());
            return Snapshot.EMPTY;
        }
    }

    public Snapshot trackRequired(ShippingCarrier carrier, String trackingNumber) {
        if (carrier == null || !carrier.trackable()) {
            throw new IllegalStateException("Choose UPS, USPS, or FedEx");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalStateException("Add a tracking number");
        }
        if (!configured(carrier)) {
            throw new IllegalStateException(carrier + " tracking is not configured");
        }
        String tracking = trackingNumber.trim();
        try {
            return switch (carrier) {
                case UPS -> upsTrack(tracking);
                case USPS -> uspsTrack(tracking);
                case FEDEX -> fedexTrack(tracking);
                default -> Snapshot.EMPTY;
            };
        } catch (RuntimeException e) {
            log.warn("Could not refresh {} tracking {}: {}", carrier, tracking, e.getMessage());
            throw new IllegalStateException(shortTrackError(carrier, e), e);
        }
    }

    private static String shortTrackError(ShippingCarrier carrier, RuntimeException e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (raw.startsWith("403") || raw.contains(" 403 ")) {
            return "Could not refresh " + carrier + " tracking (access denied)";
        }
        if (raw.startsWith("404") || raw.contains(" 404 ")) {
            return "Could not refresh " + carrier + " tracking (not found)";
        }
        if (raw.matches("(?s)^\\d{3}\\s.*")) {
            return "Could not refresh " + carrier + " tracking (" + raw.substring(0, 3) + ")";
        }
        return "Could not refresh " + carrier + " tracking";
    }

    public boolean configured(ShippingCarrier carrier) {
        if (carrier == null) {
            return false;
        }
        return switch (carrier) {
            case UPS -> properties.getUps().configured();
            case USPS -> properties.getUsps().configured();
            case FEDEX -> properties.getFedex().configured();
            default -> false;
        };
    }

    private Snapshot upsTrack(String tracking) {
        AppProperties.OAuthApi config = properties.getUps();
        if (!config.configured()) {
            return Snapshot.EMPTY;
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
        return upsSnapshot(body);
    }

    private Snapshot uspsTrack(String tracking) {
        AppProperties.OAuthApi config = properties.getUsps();
        if (!config.configured()) {
            return Snapshot.EMPTY;
        }
        String token = token("usps", () -> uspsToken(config));
        JsonNode body = getJson(
                config.host() + "/tracking/v3/tracking/" + tracking + "?expand=DETAIL",
                Map.of("Authorization", "Bearer " + token)
        );
        return uspsSnapshot(body);
    }

    private Snapshot fedexTrack(String tracking) {
        AppProperties.OAuthApi config = properties.getFedex();
        if (!config.configured()) {
            return Snapshot.EMPTY;
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
        return fedexSnapshot(body);
    }

    static Snapshot upsSnapshot(JsonNode body) {
        boolean delivered = upsLooksDelivered(body);
        return new Snapshot(delivered, upsExpectedArrival(body, delivered));
    }

    static Snapshot uspsSnapshot(JsonNode body) {
        boolean delivered = uspsLooksDelivered(body);
        return new Snapshot(delivered, uspsExpectedArrival(body, delivered));
    }

    static Snapshot fedexSnapshot(JsonNode body) {
        boolean delivered = fedexLooksDelivered(body);
        return new Snapshot(delivered, fedexExpectedArrival(body, delivered));
    }

    static LocalDate upsExpectedArrival(JsonNode body, boolean delivered) {
        if (body == null || body.isMissingNode()) {
            return null;
        }
        JsonNode shipments = body.path("trackResponse").path("shipment");
        if (!shipments.isArray()) {
            return null;
        }
        LocalDate scheduled = null;
        LocalDate rescheduled = null;
        LocalDate actual = null;
        for (JsonNode shipment : shipments) {
            JsonNode packages = shipment.path("package");
            if (!packages.isArray()) {
                continue;
            }
            for (JsonNode pkg : packages) {
                JsonNode dates = pkg.path("deliveryDate");
                if (!dates.isArray()) {
                    continue;
                }
                for (JsonNode date : dates) {
                    LocalDate parsed = parseDate(date.path("date").asText(""));
                    if (parsed == null) {
                        continue;
                    }
                    String type = date.path("type").asText("").trim().toUpperCase(Locale.ROOT);
                    if ("RDD".equals(type) && rescheduled == null) {
                        rescheduled = parsed;
                    } else if ("SDD".equals(type) && scheduled == null) {
                        scheduled = parsed;
                    } else if ("DEL".equals(type) && actual == null) {
                        actual = parsed;
                    }
                }
            }
        }
        if (delivered && actual != null) {
            return actual;
        }
        return rescheduled != null ? rescheduled : scheduled;
    }

    static LocalDate uspsExpectedArrival(JsonNode body, boolean delivered) {
        if (body == null || body.isMissingNode()) {
            return null;
        }
        if (delivered) {
            LocalDate actual = firstPresentDate(body, "deliveryDate", "actualDeliveryDate");
            if (actual != null) {
                return actual;
            }
        }
        JsonNode commitment = body.path("commitment");
        LocalDate fromCommitment = firstPresentDate(commitment, "scheduledDeliveryDate", "expectedDeliveryDate");
        if (fromCommitment != null) {
            return fromCommitment;
        }
        return firstPresentDate(
                body,
                "expectedDeliveryDate",
                "estimatedDeliveryDate",
                "predictedDeliveryDate",
                "scheduledDeliveryDate"
        );
    }

    static LocalDate fedexExpectedArrival(JsonNode body, boolean delivered) {
        if (body == null || body.isMissingNode()) {
            return null;
        }
        JsonNode results = body.path("output").path("completeTrackResults");
        if (!results.isArray()) {
            return null;
        }
        LocalDate estimated = null;
        LocalDate actual = null;
        for (JsonNode complete : results) {
            JsonNode tracks = complete.path("trackResults");
            if (!tracks.isArray()) {
                continue;
            }
            for (JsonNode track : tracks) {
                JsonNode dates = track.path("dateAndTimes");
                if (dates.isArray()) {
                    for (JsonNode date : dates) {
                        String type = dateType(date);
                        LocalDate parsed = parseDate(date.path("dateTime").asText(""));
                        if (parsed == null) {
                            parsed = parseDate(date.path("date").asText(""));
                        }
                        if (parsed == null) {
                            continue;
                        }
                        if (type.contains("ACTUAL_DELIVERY") && actual == null) {
                            actual = parsed;
                        } else if ((type.contains("ESTIMATED_DELIVERY") || type.equals("ESTIMATED")) && estimated == null) {
                            estimated = parsed;
                        }
                    }
                }
                if (estimated == null) {
                    estimated = parseDate(track.path("estimatedDeliveryTimeWindow").path("window").path("begins").asText(""));
                }
            }
        }
        if (delivered && actual != null) {
            return actual;
        }
        return estimated;
    }

    private static String dateType(JsonNode date) {
        String type = date.path("type").asText("");
        if (type.isBlank() && date.path("type").isObject()) {
            type = date.path("type").path("code").asText("");
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalDate firstPresentDate(JsonNode body, String... fields) {
        if (body == null || body.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            LocalDate parsed = parseDate(body.path(field).asText(""));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.length() == 8 && value.chars().allMatch(Character::isDigit)) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            try {
                return LocalDate.parse(value.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                // Fall through to date-time parsers.
            }
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }
        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
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

    public record Snapshot(boolean delivered, LocalDate expectedArrival) {
        static final Snapshot EMPTY = new Snapshot(false, null);
    }

    private record CachedToken(String value, Instant expiry) {
    }
}
