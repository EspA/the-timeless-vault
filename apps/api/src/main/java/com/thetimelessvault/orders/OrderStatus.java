package com.thetimelessvault.orders;

public enum OrderStatus {
    OPEN(1),
    SHIPPED(2),
    COMPLETED(3),
    CANCELLED(-1);

    private final int rank;

    OrderStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static boolean shouldApplyChannelStatus(OrderStatus current, OrderStatus incoming) {
        if (incoming == null || incoming == current) {
            return false;
        }
        if (incoming == CANCELLED || current == CANCELLED) {
            return true;
        }
        return incoming.rank() > current.rank();
    }
}
