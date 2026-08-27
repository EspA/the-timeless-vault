package com.thetimelessvault.inbound;

public enum QuoteStatus {
    DRAFT,
    CONVERTED;

    public boolean isDraft() {
        return this == DRAFT;
    }
}
