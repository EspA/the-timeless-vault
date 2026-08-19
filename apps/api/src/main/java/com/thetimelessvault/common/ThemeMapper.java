package com.thetimelessvault.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ThemeMapper {

    public static final List<String> EBAY_STORE_CATEGORIES = List.of(
            "DC Comics",
            "Disney",
            "Harry Potter",
            "Icons",
            "Indiana Jones",
            "Marvel",
            "Ninjago",
            "Star Wars",
            "The Lord of the Rings",
            "Other"
    );

    private static final Map<String, String> THEME_TO_STORE = new LinkedHashMap<>();

    static {
        THEME_TO_STORE.put("star wars", "Star Wars");
        THEME_TO_STORE.put("marvel", "Marvel");
        THEME_TO_STORE.put("super heroes", "Marvel");
        THEME_TO_STORE.put("dc", "DC Comics");
        THEME_TO_STORE.put("dc comics", "DC Comics");
        THEME_TO_STORE.put("disney", "Disney");
        THEME_TO_STORE.put("harry potter", "Harry Potter");
        THEME_TO_STORE.put("icons", "Icons");
        THEME_TO_STORE.put("creator expert", "Icons");
        THEME_TO_STORE.put("indiana jones", "Indiana Jones");
        THEME_TO_STORE.put("ninjago", "Ninjago");
        THEME_TO_STORE.put("the lord of the rings", "The Lord of the Rings");
        THEME_TO_STORE.put("lord of the rings", "The Lord of the Rings");
        THEME_TO_STORE.put("the hobbit", "The Lord of the Rings");
    }

    private ThemeMapper() {
    }

    public static String ebayStoreCategory(String theme) {
        if (theme == null || theme.isBlank()) {
            return "Other";
        }
        String key = theme.toLowerCase(Locale.ROOT).trim();
        if (THEME_TO_STORE.containsKey(key)) {
            return THEME_TO_STORE.get(key);
        }
        for (Map.Entry<String, String> entry : THEME_TO_STORE.entrySet()) {
            if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
                return entry.getValue();
            }
        }
        return "Other";
    }

    public static final int TITLE_MAX_LENGTH = 80;

    public static String suggestedTitle(String theme, String setNumber, String name, ItemCondition condition) {
        String number = displaySetNumber(setNumber);
        String themePart = theme == null || theme.isBlank() ? "" : " " + theme.trim();
        String namePart = name == null || name.isBlank() ? "" : " " + name.trim();
        String title = ("LEGO " + number + themePart + namePart).replaceAll(" +", " ").trim();
        if (condition == ItemCondition.NEW_SEALED) {
            title = title + " (New Sealed In Box)";
        }
        return limitTitle(title);
    }

    public static String limitTitle(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH);
    }

    public static String suggestedEbaySearch(String theme, String setNumber, String name) {
        String number = displaySetNumber(setNumber);
        String namePart = name == null || name.isBlank() ? "" : " " + name.trim();
        return ("LEGO " + number + namePart).replaceAll(" +", " ").trim();
    }

    public static String displaySetNumber(String setNumber) {
        if (setNumber == null || setNumber.isBlank()) {
            return "";
        }
        return setNumber.trim().replaceFirst("-\\d+$", "");
    }
}
