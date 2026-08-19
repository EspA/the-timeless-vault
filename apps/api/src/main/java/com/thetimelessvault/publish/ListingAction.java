package com.thetimelessvault.publish;

public enum ListingAction {
    CREATE,
    UPDATE,
    ACTIVATE,
    DEACTIVATE,
    DELETE;

    static ListingAction fromVisibility(String status) {
        return "UNLISTED".equalsIgnoreCase(status) ? DEACTIVATE : ACTIVATE;
    }
}
