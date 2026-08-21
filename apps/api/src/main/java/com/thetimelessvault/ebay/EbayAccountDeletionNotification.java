package com.thetimelessvault.ebay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record EbayAccountDeletionNotification(
        Metadata metadata,
        Notification notification
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metadata(
            String topic,
            String schemaVersion,
            Boolean deprecated
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Notification(
            String notificationId,
            String eventDate,
            String publishDate,
            Integer publishAttemptCount,
            AccountDeletionData data
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccountDeletionData(
            String username,
            String userId,
            String eiasToken
    ) {
    }
}
