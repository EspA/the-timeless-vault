package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.inventory.InventoryItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * eBay catalog-style item specifics copied from matching live listings, then filled
 * from BrickEconomy. Seller title, photos, description, and price stay ours.
 */
public record EbayCatalogTemplate(
        String epid,
        String mpn,
        List<String> upc,
        List<String> ean,
        Map<String, List<String>> aspects
) {
    private static final Set<String> SKIP_ASPECTS = Set.of(
            "packaging",
            "item height",
            "item length",
            "item width",
            "item weight",
            "custom bundle",
            "bundle description",
            "modified item",
            "unit quantity",
            "unit type",
            "personalization",
            "gender",
            "type"
    );

    static boolean sellerOwnedAspect(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        return key.equals("brand") || key.equals("type") || key.equals("packaging");
    }

    static boolean catalogFillAspect(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        return key.equals("year retired")
                || key.equals("retired")
                || key.equals("number of pieces")
                || key.equals("release year")
                || key.equals("year manufactured");
    }

    static boolean includeWithCatalogProduct(String name) {
        return sellerOwnedAspect(name) || catalogFillAspect(name);
    }
    private static final Set<String> VARIETY_TITLE_MARKERS = Set.of(
            "choose your",
            "you pick",
            "pick your",
            "variety",
            "mystery",
            "assorted",
            "random"
    );
    private static final int MAX_DETAIL_CANDIDATES = 6;

    public static EbayCatalogTemplate fromCatalog(InventoryItem item) {
        CatalogItem catalog = item.getCatalogItem();
        Map<String, List<String>> aspects = new LinkedHashMap<>();
        putAspect(aspects, "Brand", "LEGO");
        String theme = blankToNull(catalog.getTheme());
        if (theme != null) {
            putAspect(aspects, "Theme", theme);
            putAspect(aspects, "LEGO Theme", theme);
        }
        String subtheme = blankToNull(catalog.getSubtheme());
        if (subtheme != null) {
            putAspect(aspects, "LEGO Subtheme", subtheme);
        }
        String setNumber = ThemeMapper.displaySetNumber(catalog.getSetNumber());
        if (!setNumber.isBlank()) {
            putAspect(aspects, "LEGO Set Number", setNumber);
            putAspect(aspects, "MPN", setNumber);
        }
        String name = blankToNull(catalog.getName());
        if (name != null) {
            putAspect(aspects, "LEGO Set Name", name);
        }
        if (catalog.getPiecesCount() != null && catalog.getPiecesCount() > 0) {
            putAspect(aspects, "Number of Pieces", String.valueOf(catalog.getPiecesCount()));
        }
        Integer releaseYear = catalog.getYear();
        if (releaseYear == null && catalog.getReleasedDate() != null) {
            releaseYear = catalog.getReleasedDate().getYear();
        }
        if (releaseYear != null && releaseYear > 0) {
            String year = String.valueOf(releaseYear);
            putAspect(aspects, "Release Year", year);
            putAspect(aspects, "Year Manufactured", year);
        }
        if (Boolean.TRUE.equals(catalog.getRetired())) {
            putAspect(aspects, "Retired", "Yes");
        } else if (Boolean.FALSE.equals(catalog.getRetired())) {
            putAspect(aspects, "Retired", "No");
        }
        if (catalog.getRetiredDate() != null) {
            putAspect(aspects, "Year Retired", String.valueOf(catalog.getRetiredDate().getYear()));
        }
        applyConditionAspects(aspects, item);
        String upc = digits(catalog.getUpc());
        String ean = digits(catalog.getEan());
        if (!upc.isBlank()) {
            putAspect(aspects, "UPC", upc);
            ean = "";
        } else if (!ean.isBlank()) {
            putAspect(aspects, "EAN", ean);
        }
        return new EbayCatalogTemplate(
                null,
                setNumber.isBlank() ? null : setNumber,
                upc.isBlank() ? List.of() : List.of(upc),
                ean.isBlank() ? List.of() : List.of(ean),
                aspects
        );
    }

    public static List<JsonNode> matchingSummaries(JsonNode search, CatalogItem catalog) {
        List<JsonNode> matched = new ArrayList<>();
        if (search == null) {
            return matched;
        }
        JsonNode items = search.path("itemSummaries");
        if (!items.isArray()) {
            return matched;
        }
        String setNumber = ThemeMapper.displaySetNumber(catalog.getSetNumber());
        for (JsonNode item : items) {
            if (isTemplateCandidate(item.path("title").asText(""), setNumber)) {
                matched.add(item);
            }
        }
        return matched;
    }

    public static List<JsonNode> detailCandidates(List<JsonNode> summaries) {
        List<JsonNode> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode summary : summaries) {
            String itemId = blankToNull(summary.path("itemId").asText(null));
            if (itemId == null || !seen.add(itemId)) {
                continue;
            }
            unique.add(summary);
            if (unique.size() >= MAX_DETAIL_CANDIDATES) {
                break;
            }
        }
        return unique;
    }

    public static EbayCatalogTemplate merge(EbayCatalogTemplate fallback, List<JsonNode> details) {
        return merge(fallback, List.of(), details);
    }

    public static EbayCatalogTemplate merge(
            EbayCatalogTemplate fallback,
            List<JsonNode> summaries,
            List<JsonNode> details
    ) {
        Map<String, List<String>> aspects = new LinkedHashMap<>();
        String epid = majorityEpid(details);
        if (epid == null) {
            epid = majorityEpid(summaries);
        }
        String mpn = null;
        LinkedHashSet<String> upcs = new LinkedHashSet<>();
        LinkedHashSet<String> eans = new LinkedHashSet<>();
        List<JsonNode> ranked = new ArrayList<>(details);
        ranked.sort((a, b) -> Integer.compare(score(b), score(a)));
        for (JsonNode detail : ranked) {
            String detailEpid = blankToNull(detail.path("epid").asText(null));
            if (epid != null && detailEpid != null && !epid.equals(detailEpid)) {
                continue;
            }
            if (mpn == null) {
                mpn = blankToNull(detail.path("mpn").asText(null));
            }
            addIdentifiers(upcs, eans, detail);
            mergeAspects(aspects, detail.path("localizedAspects"));
        }
        if (fallback != null) {
            if (mpn == null) {
                mpn = fallback.mpn();
            }
            upcs.addAll(fallback.upc());
            eans.addAll(fallback.ean());
            fallback.aspects().forEach((name, values) -> aspects.putIfAbsent(name, values));
        }
        return new EbayCatalogTemplate(
                epid,
                mpn,
                List.copyOf(upcs),
                List.copyOf(eans),
                aspects
        );
    }

    public EbayCatalogTemplate withoutCopiedAspects() {
        Map<String, List<String>> seller = new LinkedHashMap<>();
        aspects.forEach((name, values) -> {
            if (sellerOwnedAspect(name)) {
                seller.put(name, values);
            }
        });
        return new EbayCatalogTemplate(epid, mpn, upc, ean, seller);
    }

    static boolean isTemplateCandidate(String title, String setNumber) {
        if (title == null || title.isBlank() || setNumber == null || setNumber.isBlank()) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        for (String marker : VARIETY_TITLE_MARKERS) {
            if (lower.contains(marker)) {
                return false;
            }
        }
        return setNumberPattern(setNumber).matcher(title).find();
    }

    static int score(JsonNode detail) {
        int score = 0;
        if (blankToNull(detail.path("epid").asText(null)) != null) {
            score += 10;
        }
        JsonNode aspects = detail.path("localizedAspects");
        if (aspects.isArray()) {
            for (JsonNode aspect : aspects) {
                String name = aspect.path("name").asText("");
                if (keepAspect(name)) {
                    score += 1;
                }
                String key = name.toLowerCase(Locale.ROOT);
                if (key.equals("type") || key.equals("age level") || key.equals("interests") || key.equals("interest")) {
                    score += 4;
                }
            }
        }
        return score;
    }

    private static void mergeAspects(Map<String, List<String>> aspects, JsonNode localized) {
        if (!localized.isArray()) {
            return;
        }
        for (JsonNode aspect : localized) {
            String name = blankToNull(aspect.path("name").asText(null));
            if (name == null || !keepAspect(name) || aspects.containsKey(name)) {
                continue;
            }
            List<String> values = aspectValues(aspect.path("value"));
            if (!values.isEmpty()) {
                aspects.put(name, values);
            }
        }
    }

    private static List<String> aspectValues(JsonNode value) {
        List<String> values = new ArrayList<>();
        if (value == null || value.isMissingNode() || value.isNull()) {
            return values;
        }
        if (value.isArray()) {
            for (JsonNode node : value) {
                addValue(values, node.asText(null));
            }
            return values;
        }
        addValue(values, value.asText(null));
        return values;
    }

    private static void addValue(List<String> values, String raw) {
        String value = blankToNull(raw);
        if (value != null) {
            values.add(value);
        }
    }

    private static void addIdentifiers(Set<String> upcs, Set<String> eans, JsonNode detail) {
        String gtin = detail.path("gtin").asText("");
        for (String part : gtin.split("[,;]+")) {
            String digits = digits(part);
            if (digits.length() == 12) {
                upcs.add(digits);
            } else if (digits.length() == 13 && digits.startsWith("0")) {
                upcs.add(digits.substring(1));
            } else if (digits.length() == 13) {
                eans.add(digits);
            }
        }
        String upc = digits(aspectValue(detail, "UPC"));
        if (upc.length() == 12 || upc.length() == 13) {
            upcs.add(upc.length() == 13 && upc.startsWith("0") ? upc.substring(1) : upc);
        }
        String ean = digits(aspectValue(detail, "EAN"));
        if (ean.length() == 13) {
            eans.add(ean);
        }
    }

    private static String aspectValue(JsonNode detail, String name) {
        for (JsonNode aspect : detail.path("localizedAspects")) {
            if (name.equalsIgnoreCase(aspect.path("name").asText(""))) {
                return aspect.path("value").asText("");
            }
        }
        return "";
    }

    private static String majorityEpid(List<JsonNode> details) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode detail : details) {
            String epid = blankToNull(detail.path("epid").asText(null));
            if (epid != null) {
                counts.merge(epid, 1, Integer::sum);
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private static void applyConditionAspects(Map<String, List<String>> aspects, InventoryItem item) {
        ItemType type = item.getItemType();
        ItemCondition condition = item.getCondition();
        if (type == ItemType.SET || type == ItemType.POLYBAG) {
            if (condition == ItemCondition.NEW_INCOMPLETE || condition == ItemCondition.USED_INCOMPLETE) {
                putAspect(aspects, "Type", "Incomplete Set");
            } else if (condition != ItemCondition.NEW_OTHER) {
                putAspect(aspects, "Type", "Complete Set");
            }
        }
        if (condition == ItemCondition.NEW_SEALED) {
            putAspect(aspects, "Packaging", "Box");
        }
    }

    private static void putAspect(Map<String, List<String>> aspects, String name, String value) {
        if (value != null && !value.isBlank()) {
            aspects.putIfAbsent(name, List.of(value));
        }
    }

    static boolean keepAspect(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return !SKIP_ASPECTS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    static Pattern setNumberPattern(String setNumber) {
        return Pattern.compile("(?i)(?<![0-9])" + Pattern.quote(setNumber) + "(?![0-9])");
    }

    static String digits(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
