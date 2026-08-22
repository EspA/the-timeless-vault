package com.thetimelessvault.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.common.ThemeMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {

    private static final ObjectMapper JSON = new ObjectMapper();

    private InventoryDtos() {
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer resolveMinifigsCount(CatalogItem item, JsonNode brickeconomy) {
        if (item.getMinifigsCount() != null && item.getMinifigsCount() > 0) {
            return item.getMinifigsCount();
        }
        if (brickeconomy == null) {
            return item.getMinifigsCount();
        }
        if (brickeconomy.hasNonNull("minifigs_count") && brickeconomy.get("minifigs_count").asInt() > 0) {
            return brickeconomy.get("minifigs_count").asInt();
        }
        if (brickeconomy.has("minifigs") && brickeconomy.get("minifigs").isArray() && brickeconomy.get("minifigs").size() > 0) {
            return brickeconomy.get("minifigs").size();
        }
        return item.getMinifigsCount();
    }

    private static LocalDate firstDate(LocalDate existing, JsonNode brickeconomy, String field) {
        if (existing != null) {
            return existing;
        }
        if (brickeconomy == null || !brickeconomy.hasNonNull(field)) {
            return null;
        }
        try {
            return LocalDate.parse(brickeconomy.get(field).asText());
        } catch (Exception e) {
            return null;
        }
    }

    public record CreateRequest(
            @NotBlank String setNumber,
            @Size(max = 80) String title,
            String description,
            @Size(max = 255) String shortDescription,
            @DecimalMin("0.01") BigDecimal price,
            @NotNull @DecimalMin("0.01") BigDecimal ebayPrice,
            @NotNull @DecimalMin("0.01") BigDecimal bricklinkPrice,
            @NotNull @DecimalMin("0.01") BigDecimal shopifyPrice,
            @Min(0) Integer quantity,
            BigDecimal cost,
            ItemType itemType,
            ItemCondition condition,
            StockStatus stockStatus,
            List<String> shopifyCollectionIds,
            String ebayStoreCategory,
            BigDecimal minimumOffer,
            Integer packageLbs,
            Integer packageOz,
            BigDecimal packageLength,
            BigDecimal packageWidth,
            BigDecimal packageHeight,
            String notes
    ) {
    }

    public record UpdateRequest(
            @Size(max = 80) String title,
            String description,
            @Size(max = 255) String shortDescription,
            @DecimalMin("0.01") BigDecimal price,
            @DecimalMin("0.01") BigDecimal ebayPrice,
            @DecimalMin("0.01") BigDecimal bricklinkPrice,
            @DecimalMin("0.01") BigDecimal shopifyPrice,
            @Min(0) Integer quantity,
            BigDecimal cost,
            ItemType itemType,
            ItemCondition condition,
            StockStatus stockStatus,
            List<String> shopifyCollectionIds,
            String ebayStoreCategory,
            BigDecimal minimumOffer,
            Integer packageLbs,
            Integer packageOz,
            BigDecimal packageLength,
            BigDecimal packageWidth,
            BigDecimal packageHeight,
            String notes
    ) {
    }

    public record CatalogView(
            UUID id,
            String setNumber,
            String name,
            String theme,
            String subtheme,
            Integer year,
            Integer piecesCount,
            Integer minifigsCount,
            Boolean retired,
            LocalDate releasedDate,
            LocalDate retiredDate,
            BigDecimal currentValueNew,
            BigDecimal currentValueUsed,
            BigDecimal retailPriceUs,
            BigDecimal twentyPercentBelowNew,
            String suggestedTitle,
            String suggestedEbayStoreCategory,
            Instant fetchedAt,
            BrickLinkPackage bricklinkPackage
    ) {
        public CatalogView withBricklinkPackage(BrickLinkPackage bricklinkPackage) {
            return new CatalogView(
                    id, setNumber, name, theme, subtheme, year, piecesCount, minifigsCount, retired,
                    releasedDate, retiredDate, currentValueNew, currentValueUsed, retailPriceUs, twentyPercentBelowNew,
                    suggestedTitle, suggestedEbayStoreCategory, fetchedAt, bricklinkPackage
            );
        }

        public static CatalogView from(CatalogItem item) {
            JsonNode brickeconomy = parseJson(item.getBrickeconomyJson());
            BigDecimal below = item.getCurrentValueNew() == null
                    ? null
                    : item.getCurrentValueNew().multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP);
            return new CatalogView(
                    item.getId(),
                    item.getSetNumber(),
                    item.getName(),
                    item.getTheme(),
                    item.getSubtheme(),
                    item.getYear(),
                    item.getPiecesCount(),
                    resolveMinifigsCount(item, brickeconomy),
                    item.getRetired(),
                    firstDate(item.getReleasedDate(), brickeconomy, "released_date"),
                    firstDate(item.getRetiredDate(), brickeconomy, "retired_date"),
                    item.getCurrentValueNew(),
                    item.getCurrentValueUsed(),
                    item.getRetailPriceUs(),
                    below,
                    ThemeMapper.suggestedTitle(
                            item.getTheme(),
                            item.getSubtheme(),
                            item.getSetNumber(),
                            item.getName(),
                            ItemCondition.NEW_SEALED
                    ),
                    ThemeMapper.ebayStoreCategory(item.getTheme()),
                    item.getFetchedAt(),
                    null
            );
        }
    }

    public record BrickLinkMeasures(
            int lbs,
            int oz,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height
    ) {
    }

    public record BrickLinkPackage(
            BrickLinkMeasures shipping,
            BrickLinkMeasures original
    ) {
    }

    public record PhotoView(UUID id, String url, String filename, int sortOrder, boolean primaryForBricklink) {
    }

    public record InventoryView(
            UUID id,
            String sku,
            CatalogView catalog,
            String title,
            String description,
            String shortDescription,
            BigDecimal price,
            BigDecimal ebayPrice,
            BigDecimal bricklinkPrice,
            BigDecimal shopifyPrice,
            int quantity,
            StockStatus stockStatus,
            BigDecimal cost,
            ItemType itemType,
            ItemCondition condition,
            List<String> shopifyCollectionIds,
            String ebayStoreCategory,
            BigDecimal minimumOffer,
            int packageLbs,
            int packageOz,
            BigDecimal packageLength,
            BigDecimal packageWidth,
            BigDecimal packageHeight,
            String notes,
            String shopifyStatus,
            String bricklinkStatus,
            String ebayStatus,
            String shopifyLiveUrl,
            String bricklinkLiveUrl,
            String ebayLiveUrl,
            List<PhotoView> photos,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record InventoryPage(
            List<InventoryView> items,
            int page,
            int size,
            long total,
            int totalPages
    ) {
    }
}
