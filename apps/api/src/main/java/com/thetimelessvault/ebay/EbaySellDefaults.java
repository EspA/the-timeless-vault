package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;

public record EbaySellDefaults(
        String merchantLocationKey,
        String fulfillmentPolicyId,
        String paymentPolicyId,
        String returnPolicyId
) {
    static String pick(
            JsonNode items,
            String configured,
            String idField,
            String nameField,
            String statusField,
            String enabledStatus
    ) {
        String wanted = configured == null ? "" : configured.trim();
        JsonNode matched = null;
        JsonNode firstEnabled = null;
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                boolean enabled = statusField == null
                        || enabledStatus.equalsIgnoreCase(item.path(statusField).asText(""));
                if (enabled && firstEnabled == null) {
                    firstEnabled = item;
                }
                if (!wanted.isBlank() && matches(item, wanted, idField, nameField) && (statusField == null || enabled)) {
                    matched = item;
                    break;
                }
            }
        }
        JsonNode chosen = matched != null ? matched : firstEnabled;
        if (chosen != null) {
            String id = chosen.path(idField).asText(null);
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return wanted.isBlank() ? null : wanted;
    }

    private static boolean matches(JsonNode item, String wanted, String idField, String nameField) {
        return wanted.equalsIgnoreCase(item.path(idField).asText())
                || wanted.equalsIgnoreCase(item.path(nameField).asText());
    }
}
