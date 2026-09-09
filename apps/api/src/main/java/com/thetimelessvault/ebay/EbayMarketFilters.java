package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EbayMarketFilters {

    public static final String DEFAULT_EXCLUDE_WORDS =
            "-custom -moc -replica -case -kit -led -minifigure -no -minifigures -figures -bricks -blocks -minifig -minifigs -figure -sticker -stickers -display -copy -creative -bag -compatible -only -generic -manuals -manual -displaycase -brick -toys -unofficial -kids -gift -fake -adults -mini-figures -mock";
    public static final String LOCATION_NORTH_AMERICA = "NORTH_AMERICA";
    public static final String LOCATION_UNITED_STATES = "UNITED_STATES";
    public static final String LOCATION_WORLDWIDE = "WORLDWIDE";
    public static final String LISTING_TYPE_ALL = "ALL";
    public static final String LISTING_TYPE_AUCTION = "AUCTION";
    public static final String LISTING_TYPE_FIXED_PRICE = "FIXED_PRICE";
    private static final Set<String> UNITED_STATES = Set.of("US", "USA");

    private static final Set<String> NORTH_AMERICA = Set.of("US", "USA", "CA", "CAN", "MX", "MEX");
    private static final Set<String> NORTH_AMERICA_MARKETPLACES = Set.of("EBAY_US", "EBAY_CA", "EBAY_MOTORS");
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "a", "an", "and", "for", "in", "of", "or", "the", "to"
    );
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    private EbayMarketFilters() {
    }

    public static String normalizeItemLocation(String location) {
        if (location == null || location.isBlank()) {
            return LOCATION_NORTH_AMERICA;
        }
        String value = location.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (value.equals("US") || value.equals("USA") || value.equals(LOCATION_UNITED_STATES)) {
            return LOCATION_UNITED_STATES;
        }
        if (value.equals(LOCATION_WORLDWIDE) || value.equals("ANYWHERE") || value.equals("ALL")) {
            return LOCATION_WORLDWIDE;
        }
        return LOCATION_NORTH_AMERICA;
    }

    public static String normalizeListingType(String listingType) {
        if (listingType == null || listingType.isBlank()) {
            return LISTING_TYPE_ALL;
        }
        String value = listingType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (value.equals("BIN") || value.equals("BUY_IT_NOW") || value.equals("FIXED")) {
            return LISTING_TYPE_FIXED_PRICE;
        }
        if (value.equals(LISTING_TYPE_AUCTION) || value.equals(LISTING_TYPE_FIXED_PRICE)) {
            return value;
        }
        return LISTING_TYPE_ALL;
    }

    public static String buyingOptionsFilter(String listingType) {
        return switch (normalizeListingType(listingType)) {
            case LISTING_TYPE_AUCTION -> "buyingOptions:{AUCTION}";
            case LISTING_TYPE_FIXED_PRICE -> "buyingOptions:{FIXED_PRICE|BEST_OFFER}";
            default -> "buyingOptions:{AUCTION|FIXED_PRICE|BEST_OFFER}";
        };
    }

    public static boolean matchesListingType(JsonNode item, String listingType) {
        String normalized = normalizeListingType(listingType);
        if (LISTING_TYPE_ALL.equals(normalized)) {
            return true;
        }
        boolean auction = false;
        boolean fixedPrice = false;
        boolean bestOffer = false;
        for (JsonNode option : item.path("buyingOptions")) {
            String value = option.asText("");
            if ("AUCTION".equalsIgnoreCase(value)) {
                auction = true;
            } else if ("FIXED_PRICE".equalsIgnoreCase(value)) {
                fixedPrice = true;
            } else if ("BEST_OFFER".equalsIgnoreCase(value)) {
                bestOffer = true;
            }
        }
        if (LISTING_TYPE_AUCTION.equals(normalized)) {
            return auction;
        }
        return fixedPrice || bestOffer || !auction;
    }

    public static boolean locatedIn(JsonNode item, String location) {
        String normalized = normalizeItemLocation(location);
        if (LOCATION_WORLDWIDE.equals(normalized)) {
            return true;
        }
        if (LOCATION_UNITED_STATES.equals(normalized)) {
            return locatedInUnitedStates(item);
        }
        return locatedInNorthAmerica(item);
    }

    public static boolean locatedInNorthAmerica(JsonNode item) {
        String country = item.path("itemLocation").path("country").asText("");
        if (!country.isBlank()) {
            return NORTH_AMERICA.contains(country.trim().toUpperCase(Locale.ROOT));
        }
        String marketplace = item.path("listingMarketplaceId").asText("");
        if (!marketplace.isBlank()) {
            return NORTH_AMERICA_MARKETPLACES.contains(marketplace.trim().toUpperCase(Locale.ROOT));
        }
        return true;
    }

    public static boolean locatedInUnitedStates(JsonNode item) {
        String country = item.path("itemLocation").path("country").asText("");
        if (!country.isBlank()) {
            return UNITED_STATES.contains(country.trim().toUpperCase(Locale.ROOT));
        }
        String marketplace = item.path("listingMarketplaceId").asText("");
        if (!marketplace.isBlank()) {
            return "EBAY_US".equals(marketplace.trim().toUpperCase(Locale.ROOT));
        }
        return true;
    }

    public static boolean feedbackAtLeast(JsonNode item, int minimum) {
        JsonNode score = item.path("seller").path("feedbackScore");
        if (score.isMissingNode() || score.isNull()) {
            return minimum <= 0;
        }
        return score.asInt(0) >= minimum;
    }

    public static boolean titleMatchesSearch(String title, String searchQuery) {
        List<String> required = parseSearchWords(searchQuery);
        if (required.isEmpty()) {
            return true;
        }
        Set<String> titleWords = new LinkedHashSet<>(tokenize(title));
        for (String word : required) {
            if (!titleWords.contains(word)) {
                return false;
            }
        }
        return true;
    }

    public static List<String> parseSearchWords(String searchQuery) {
        List<String> words = new ArrayList<>();
        if (searchQuery == null || searchQuery.isBlank()) {
            return words;
        }
        for (String word : tokenize(browseQuery(searchQuery))) {
            if (!SEARCH_STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    public static boolean titleExcludes(String title, String excludeWords) {
        if (excludeWords == null || excludeWords.isBlank()) {
            return true;
        }
        Set<String> titleWords = new LinkedHashSet<>(tokenize(title));
        for (String word : parseExcludeWords(excludeWords)) {
            List<String> parts = tokenize(word);
            if (parts.size() == 1) {
                if (titleWords.contains(parts.getFirst())) {
                    return false;
                }
            } else if (!parts.isEmpty() && titleWords.containsAll(parts)) {
                return false;
            }
        }
        return true;
    }

    public static List<String> parseExcludeWords(String excludeWords) {
        List<String> words = new ArrayList<>();
        if (excludeWords == null || excludeWords.isBlank()) {
            return words;
        }
        for (String part : excludeWords.split("[,\\s]+")) {
            String word = part.trim().toLowerCase(Locale.ROOT).replaceFirst("^-+", "");
            if (!word.isBlank()) {
                words.add(word);
            }
        }
        return words;
    }

    static List<String> tokenize(String text) {
        List<String> words = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return words;
        }
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    public static String minusClause(String excludeWords) {
        List<String> words = parseExcludeWords(excludeWords);
        if (words.isEmpty()) {
            return "";
        }
        StringBuilder clause = new StringBuilder();
        for (String word : words) {
            if (!clause.isEmpty()) {
                clause.append(' ');
            }
            clause.append('-').append(word);
        }
        return clause.toString();
    }

    public static String effectiveExcludeWords(String excludeWords) {
        return excludeWords == null ? DEFAULT_EXCLUDE_WORDS : excludeWords;
    }

    public static String browseQuery(String query) {
        return browseQuery(query, null);
    }

    public static String browseQuery(String query, String excludeWords) {
        if (query == null || query.isBlank()) {
            query = "LEGO";
        }
        String cleaned = query
                .replace(" (New Sealed In Box)", "")
                .replaceAll("\\(([^,)]*)\\)", "$1")
                .replaceAll(" +", " ")
                .trim();
        if (cleaned.isBlank()) {
            cleaned = "LEGO";
        }
        return cleaned;
    }

    public static boolean matchesWatch(JsonNode item, int feedbackMin, String excludeWords) {
        return matchesWatch(item, feedbackMin, null, excludeWords, LOCATION_NORTH_AMERICA);
    }

    public static boolean matchesWatch(JsonNode item, int feedbackMin, String searchQuery, String excludeWords) {
        return matchesWatch(item, feedbackMin, searchQuery, excludeWords, LOCATION_NORTH_AMERICA);
    }

    public static boolean matchesWatch(
            JsonNode item,
            int feedbackMin,
            String searchQuery,
            String excludeWords,
            String itemLocation
    ) {
        return matchesWatch(item, feedbackMin, searchQuery, excludeWords, itemLocation, LISTING_TYPE_ALL);
    }

    public static boolean matchesWatch(
            JsonNode item,
            int feedbackMin,
            String searchQuery,
            String excludeWords,
            String itemLocation,
            String listingType
    ) {
        String title = item.path("title").asText("");
        return locatedIn(item, itemLocation)
                && feedbackAtLeast(item, feedbackMin)
                && matchesListingType(item, listingType)
                && titleMatchesSearch(title, searchQuery)
                && titleExcludes(title, excludeWords);
    }
}
