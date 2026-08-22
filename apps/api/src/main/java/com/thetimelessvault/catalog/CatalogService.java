package com.thetimelessvault.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogItemRepository catalogItems;
    private final BrickEconomyClient brickEconomy;

    public CatalogService(CatalogItemRepository catalogItems, BrickEconomyClient brickEconomy) {
        this.catalogItems = catalogItems;
        this.brickEconomy = brickEconomy;
    }

    @Transactional
    public CatalogItem lookup(String rawSetNumber, boolean forceRefresh) {
        String setNumber = normalizeSetNumber(rawSetNumber);
        List<String> candidates = setNumberCandidates(setNumber);
        if (!forceRefresh) {
            for (String candidate : candidates) {
                Optional<CatalogItem> existing = catalogItems.findBySetNumberIgnoreCase(candidate);
                if (existing.isPresent() && !isSparse(existing.get())) {
                    return existing.get();
                }
            }
        }
        RuntimeException last = null;
        for (String candidate : candidates) {
            try {
                CatalogItem refreshed = refresh(candidate);
                if (!isSparse(refreshed)) {
                    return refreshed;
                }
                log.warn("Catalog refresh for {} was missing year and piece count; trying another set number", candidate);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Catalog refresh failed for {}: {}", candidate, e.getMessage());
            }
        }
        if (last != null) {
            throw last;
        }
        throw ApiException.notFound("Unknown LEGO set number: " + setNumber);
    }

    @Transactional
    public CatalogItem lookupOrStub(String rawSetNumber, String fallbackName) {
        String setNumber = rawSetNumber == null || rawSetNumber.isBlank() ? "UNKNOWN" : rawSetNumber.trim();
        if ("UNKNOWN".equalsIgnoreCase(setNumber)) {
            return stub(setNumber, fallbackName);
        }
        try {
            return lookup(setNumber, false);
        } catch (RuntimeException e) {
            log.warn("Catalog lookup failed for {}: {}", setNumber, e.getMessage());
            return stub(setNumber, fallbackName);
        }
    }

    private CatalogItem stub(String setNumber, String fallbackName) {
        return catalogItems.findBySetNumberIgnoreCase(setNumber).orElseGet(() -> {
            CatalogItem item = CatalogItem.create(setNumber);
            String name = fallbackName == null || fallbackName.isBlank() ? setNumber : fallbackName.trim();
            if (name.length() > 255) {
                name = name.substring(0, 255);
            }
            item.setName(name);
            item.setCurrency("USD");
            return catalogItems.save(item);
        });
    }

    @Transactional
    public CatalogItem refresh(String setNumber) {
        JsonNode data = brickEconomy.getSet(setNumber);
        String canonical = text(data, "set_number", setNumber);
        CatalogItem item = catalogItems.findBySetNumberIgnoreCase(canonical)
                .orElseGet(() -> CatalogItem.create(canonical));
        item.setName(text(data, "name", canonical));
        item.setTheme(text(data, "theme", null));
        item.setSubtheme(text(data, "subtheme", null));
        item.setYear(intVal(data, "year"));
        item.setPiecesCount(firstInt(data, "pieces_count", "pieces"));
        Integer minifigs = firstInt(data, "minifigs_count");
        if (minifigs == null && data.has("minifigs") && data.get("minifigs").isArray()) {
            minifigs = data.get("minifigs").size();
        }
        item.setMinifigsCount(minifigs);
        item.setUpc(text(data, "upc", null));
        item.setEan(text(data, "ean", null));
        item.setRetired(boolVal(data, "retired"));
        item.setRetiredDate(dateVal(data, "retired_date"));
        item.setReleasedDate(dateVal(data, "released_date"));
        item.setCurrentValueNew(decimal(data, "current_value_new"));
        item.setRetailPriceUs(firstDecimal(data, "retail_price_us", "us_retail", "retail_price", "launch_price_us"));
        item.setCurrentValueUsed(decimal(data, "current_value_used"));
        item.setCurrency(text(data, "currency", "USD"));
        item.setBrickeconomyJson(data.toString());
        item.touch();
        return catalogItems.save(item);
    }

    public CatalogItem get(UUID id) {
        return catalogItems.findById(id).orElseThrow(() -> ApiException.notFound("Catalog item not found"));
    }

    public static String normalizeSetNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Set number is required");
        }
        return raw.trim();
    }

    static List<String> setNumberCandidates(String setNumber) {
        String trimmed = normalizeSetNumber(setNumber);
        List<String> candidates = new ArrayList<>();
        if (!trimmed.contains("-")) {
            candidates.add(trimmed + "-1");
            candidates.add(trimmed);
            return candidates;
        }
        candidates.add(trimmed);
        if (trimmed.matches(".*-1$")) {
            candidates.add(trimmed.substring(0, trimmed.length() - 2));
        }
        return candidates;
    }

    static boolean isSparse(CatalogItem item) {
        if (item == null) {
            return true;
        }
        return item.getPiecesCount() == null
                && item.getYear() == null
                && item.getReleasedDate() == null
                && item.getRetiredDate() == null;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        return value.asText();
    }

    private static Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            Integer value = intVal(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer intVal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isArray()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        String text = value.asText().replace(",", "").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean boolVal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asBoolean();
    }

    private static LocalDate dateVal(JsonNode node, String field) {
        String text = text(node, field, null);
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            if (trimmed.length() >= 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
                return LocalDate.parse(trimmed.substring(0, 10));
            }
            if (trimmed.matches("\\d{4}-\\d{2}")) {
                return LocalDate.parse(trimmed + "-01");
            }
            if (trimmed.matches("\\d{4}")) {
                return LocalDate.of(Integer.parseInt(trimmed), 1, 1);
            }
            return YearMonth.parse(trimmed, DateTimeFormatter.ofPattern("MMMM uuuu", Locale.US)).atDay(1);
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
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

    private static BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = decimal(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
