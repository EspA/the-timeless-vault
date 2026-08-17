package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.common.ThemeMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EbayStoreCategories {

    private EbayStoreCategories() {
    }

    static List<Map<String, String>> fallback() {
        return ThemeMapper.EBAY_STORE_CATEGORIES.stream()
                .map(EbayStoreCategories::entry)
                .toList();
    }

    static List<Map<String, String>> flatten(JsonNode root) {
        List<Map<String, String>> result = new ArrayList<>();
        walk(firstArray(root, "storeCategories", "storeCategory", "categories"), "", result);
        return result;
    }

    private static void walk(JsonNode list, String parentPath, List<Map<String, String>> result) {
        if (list == null || !list.isArray()) {
            return;
        }
        for (JsonNode node : list) {
            String name = text(node, "categoryName", "name");
            if (name.isBlank()) {
                continue;
            }
            String path = parentPath.isBlank() ? name : parentPath + "/" + name;
            String id = text(node, "categoryId", "id");
            result.add(entry(id.isBlank() ? path : id, name, path));
            walk(firstArray(node, "childrenCategories", "childCategory", "children"), path, result);
        }
    }

    private static JsonNode firstArray(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (child != null && child.isArray()) {
                return child;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Map<String, String> entry(String name) {
        return entry(name, name, name);
    }

    private static Map<String, String> entry(String id, String name, String path) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("path", path);
        return row;
    }
}
