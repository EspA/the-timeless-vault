package com.thetimelessvault.common;

public enum ItemCondition {
    NEW_SEALED,
    NEW_COMPLETE,
    NEW_INCOMPLETE,
    NEW_OTHER,
    USED_COMPLETE,
    USED_INCOMPLETE;

    public String brickLinkNewOrUsed() {
        return name().startsWith("USED") ? "U" : "N";
    }

    public String brickLinkCompleteness() {
        return switch (this) {
            case NEW_SEALED -> "S";
            case NEW_COMPLETE, NEW_OTHER, USED_COMPLETE -> "C";
            case NEW_INCOMPLETE, USED_INCOMPLETE -> "B";
        };
    }

    public String ebayCondition() {
        return switch (this) {
            case NEW_SEALED, NEW_COMPLETE, NEW_INCOMPLETE -> "NEW";
            case NEW_OTHER -> "NEW_OTHER";
            case USED_COMPLETE, USED_INCOMPLETE -> "USED_EXCELLENT";
        };
    }

    public boolean isNew() {
        return !name().startsWith("USED");
    }

    public static ItemCondition fromBrickLink(String newOrUsed, String completeness) {
        boolean used = newOrUsed != null && newOrUsed.trim().equalsIgnoreCase("U");
        String complete = completeness == null ? "" : completeness.trim().toUpperCase();
        if (used) {
            return "B".equals(complete) ? USED_INCOMPLETE : USED_COMPLETE;
        }
        return switch (complete) {
            case "B" -> NEW_INCOMPLETE;
            case "C" -> NEW_COMPLETE;
            default -> NEW_SEALED;
        };
    }

    public static ItemCondition fromEbay(String conditionId, String displayName) {
        String id = conditionId == null ? "" : conditionId.trim();
        String name = displayName == null ? "" : displayName.toLowerCase();
        if ("1500".equals(id) || "1750".equals(id) || name.contains("new other") || name.contains("new with defects")) {
            return NEW_OTHER;
        }
        if (id.startsWith("3") || id.startsWith("4") || id.startsWith("5") || id.startsWith("6") || id.startsWith("7")
                || name.contains("used") || name.contains("pre-owned") || name.contains("for parts")) {
            return USED_COMPLETE;
        }
        return NEW_SEALED;
    }
}
