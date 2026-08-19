package com.thetimelessvault.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.inventory.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EbayStoreCategorySettings {

    public static final String KEY = "ebay.store_categories";
    static final int NAME_MAX_LENGTH = 50;

    private final AppSettingRepository settings;
    private final InventoryItemRepository items;
    private final ObjectMapper mapper;

    public EbayStoreCategorySettings(
            AppSettingRepository settings,
            InventoryItemRepository items,
            ObjectMapper mapper
    ) {
        this.settings = settings;
        this.items = items;
        this.mapper = mapper;
    }

    public List<String> list() {
        return read();
    }

    public List<Map<String, String>> entries() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String name : list()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", name);
            row.put("name", name);
            row.put("path", name);
            result.add(row);
        }
        return result;
    }

    @Transactional
    public List<String> add(String rawName) {
        String name = normalize(rawName);
        List<String> categories = read();
        if (categories.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
            throw ApiException.conflict("eBay store category already exists");
        }
        categories.add(name);
        persist(categories);
        return categories;
    }

    @Transactional
    public List<String> delete(String rawName) {
        String name = normalize(rawName);
        List<String> categories = read();
        String stored = categories.stream()
                .filter(existing -> existing.equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("eBay store category not found"));
        categories.remove(stored);
        persist(categories);
        items.clearEbayStoreCategory(stored);
        return categories;
    }

    private List<String> read() {
        return settings.findById(KEY)
                .map(AppSetting::getValue)
                .map(this::parse)
                .orElseGet(() -> new ArrayList<>(ThemeMapper.EBAY_STORE_CATEGORIES));
    }

    private List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> values = mapper.readValue(raw, new TypeReference<>() {
            });
            List<String> cleaned = new ArrayList<>();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String name = value.trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (cleaned.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                    cleaned.add(name);
                }
            }
            return cleaned;
        } catch (Exception e) {
            return new ArrayList<>(ThemeMapper.EBAY_STORE_CATEGORIES);
        }
    }

    private void persist(List<String> categories) {
        try {
            settings.save(new AppSetting(KEY, mapper.writeValueAsString(categories)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not save eBay store categories");
        }
    }

    private static String normalize(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("eBay store category is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw ApiException.badRequest("eBay store category is too long");
        }
        return name;
    }
}
