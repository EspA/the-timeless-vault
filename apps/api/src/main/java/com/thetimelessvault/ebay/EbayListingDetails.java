package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.market.MarketListing;

import java.math.BigDecimal;

public record EbayListingDetails(
        String imageUrl,
        Integer feedbackScore,
        String feedbackPercentage,
        boolean auction,
        BigDecimal currentBid,
        boolean bestOffer,
        BigDecimal shippingCost,
        boolean shippingCalculated
) {
    public static EbayListingDetails from(JsonNode item) {
        boolean auction = false;
        boolean bestOffer = false;
        for (JsonNode option : item.path("buyingOptions")) {
            String value = option.asText("");
            if ("AUCTION".equalsIgnoreCase(value)) {
                auction = true;
            } else if ("BEST_OFFER".equalsIgnoreCase(value)) {
                bestOffer = true;
            }
        }

        JsonNode shipping = item.path("shippingOptions").path(0);
        String shippingType = shipping.path("shippingCostType").asText("");
        BigDecimal shippingCost = decimal(shipping.path("shippingCost").path("value").asText(null));
        boolean shippingCalculated = "CALCULATED".equalsIgnoreCase(shippingType) && shippingCost == null;

        return new EbayListingDetails(
                firstImage(item),
                integer(item.path("seller").path("feedbackScore")),
                blankToNull(item.path("seller").path("feedbackPercentage").asText(null)),
                auction,
                decimal(item.path("currentBidPrice").path("value").asText(null)),
                bestOffer,
                shippingCost,
                shippingCalculated
        );
    }

    public void applyTo(MarketListing listing) {
        listing.setImageUrl(imageUrl);
        listing.setSellerFeedbackScore(feedbackScore);
        listing.setSellerFeedbackPercentage(feedbackPercentage);
        listing.setAuction(auction);
        listing.setCurrentBid(currentBid);
        listing.setBestOffer(bestOffer);
        listing.setShippingCost(shippingCost);
        listing.setShippingCalculated(shippingCalculated);
    }

    private static String firstImage(JsonNode item) {
        String image = blankToNull(item.path("image").path("imageUrl").asText(null));
        if (image != null) {
            return image;
        }
        return blankToNull(item.path("thumbnailImages").path(0).path("imageUrl").asText(null));
    }

    private static Integer integer(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
