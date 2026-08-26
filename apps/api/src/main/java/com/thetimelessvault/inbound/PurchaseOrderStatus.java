package com.thetimelessvault.inbound;

public enum PurchaseOrderStatus {
    IN_TRANSIT,
    DELIVERED,
    RECEIVED,
    CANCELLED;

    public boolean isOpen() {
        return this == IN_TRANSIT || this == DELIVERED;
    }
}
