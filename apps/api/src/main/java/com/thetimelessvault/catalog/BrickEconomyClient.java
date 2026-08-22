package com.thetimelessvault.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BrickEconomyClient {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final AppProperties.Brickeconomy config;

    public BrickEconomyClient(AppProperties properties, ObjectMapper mapper) {
        this.config = properties.getBrickeconomy();
        this.mapper = mapper;
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", config.getUserAgent())
                .defaultHeader("x-apikey", config.getApiKey() == null ? "" : config.getApiKey())
                .build();
    }

    public JsonNode getSet(String setNumber) {
        if (!config.configured()) {
            throw ApiException.unavailable("BrickEconomy API key is not configured");
        }
        ResponseEntity<String> response = restClient.get()
                .uri("/set/{setNumber}?currency=USD", setNumber)
                .retrieve()
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BrickEconomy daily quota exceeded (500/day)");
                })
                .onStatus(status -> status.value() == 400 || status.value() == 404, (req, res) -> {
                    throw ApiException.notFound("Unknown LEGO set number: " + setNumber);
                })
                .toEntity(String.class);
        try {
            JsonNode root = mapper.readTree(response.getBody());
            if (root.has("data")) {
                return root.get("data");
            }
            return root;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse BrickEconomy response");
        }
    }

    public JsonNode getSalesLedger() {
        return get("/salesledger");
    }

    private JsonNode get(String path, Object... uriVars) {
        if (!config.configured()) {
            throw ApiException.unavailable("BrickEconomy API key is not configured");
        }
        ResponseEntity<String> response = restClient.get()
                .uri(path, uriVars)
                .retrieve()
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BrickEconomy daily quota exceeded (500/day)");
                })
                .onStatus(status -> status.value() == 400 || status.value() == 404, (req, res) -> {
                    throw ApiException.notFound("BrickEconomy sales ledger was not found");
                })
                .toEntity(String.class);
        try {
            JsonNode root = mapper.readTree(response.getBody());
            if (root.has("data")) {
                return root.get("data");
            }
            return root;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not parse BrickEconomy response");
        }
    }
}
