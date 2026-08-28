package com.thetimelessvault.publish;

public record PublishResult(
        String externalId,
        String liveUrl,
        String bricklinkPhotoUploadUrl,
        String shopifyStatus,
        String bricklinkStatus,
        String ebayStatus,
        String brickowlStatus
) {
    public static PublishResult of(String externalId, String liveUrl) {
        return new PublishResult(externalId, liveUrl, null, null, null, null, null);
    }

    public static PublishResult shopify(String externalId, String liveUrl, String shopifyStatus) {
        return new PublishResult(externalId, liveUrl, null, shopifyStatus, null, null, null);
    }

    public static PublishResult bricklink(String externalId, String liveUrl, String photoUrl, String bricklinkStatus) {
        return new PublishResult(externalId, liveUrl, photoUrl, null, bricklinkStatus, null, null);
    }

    public static PublishResult ebay(String offerId, String liveUrl, String ebayStatus) {
        return new PublishResult(offerId, liveUrl, null, null, null, ebayStatus, null);
    }

    public static PublishResult brickowl(String lotId, String liveUrl, String brickowlStatus) {
        return new PublishResult(lotId, liveUrl, null, null, null, null, brickowlStatus);
    }
}
