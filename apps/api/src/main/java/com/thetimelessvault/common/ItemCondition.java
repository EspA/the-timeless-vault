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
}
