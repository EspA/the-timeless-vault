package com.thetimelessvault.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CatalogService {

    private final CatalogItemRepository catalogItems;
    private final BrickEconomyClient brickEconomy;

    public CatalogService(CatalogItemRepository catalogItems, BrickEconomyClient brickEconomy) {
        this.catalogItems = catalogItems;
        this.brickEconomy = brickEconomy;
    }

    @Transactional
    public CatalogItem lookup(String rawSetNumber, boolean forceRefresh) {
        String setNumber = normalizeSetNumber(rawSetNumber);
        return catalogItems.findBySetNumberIgnoreCase(setNumber)
                .filter(existing -> !forceRefresh)
                .orElseGet(() -> refresh(setNumber));
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
        item.setPiecesCount(intVal(data, "pieces_count"));
        item.setMinifigsCount(intVal(data, "minifigs_count"));
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

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        return value.asText();
    }

    private static Integer intVal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asInt();
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
        return LocalDate.parse(text);
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
