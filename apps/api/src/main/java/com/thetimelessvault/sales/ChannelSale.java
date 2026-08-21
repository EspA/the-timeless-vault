package com.thetimelessvault.sales;

import com.thetimelessvault.common.Platform;

import java.math.BigDecimal;
import java.time.Instant;

public record ChannelSale(
        Platform platform,
        String orderId,
        String lineId,
        String sku,
        String listingExternalId,
        String title,
        String setNumber,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        Instant soldAt,
        String orderUrl,
        BigDecimal shippingCost,
        BigDecimal platformFee
) {
    public ChannelSale {
        orderId = blankToNull(orderId);
        lineId = blankToNull(lineId);
        sku = blankToNull(sku);
        listingExternalId = blankToNull(listingExternalId);
        title = blankToNull(title);
        setNumber = blankToNull(setNumber);
        currency = currency == null || currency.isBlank() ? "USD" : currency.trim();
        quantity = Math.max(1, quantity);
        unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        soldAt = soldAt == null ? Instant.now() : soldAt;
        orderUrl = blankToNull(orderUrl);
        shippingCost = shippingCost == null ? BigDecimal.ZERO : shippingCost;
        platformFee = platformFee == null ? BigDecimal.ZERO : platformFee;
    }

    public String identityLineId() {
        return lineId == null || lineId.isBlank() ? "0" : lineId;
    }

    public ChannelSale withPlatformFee(BigDecimal fee) {
        return new ChannelSale(
                platform,
                orderId,
                lineId,
                sku,
                listingExternalId,
                title,
                setNumber,
                quantity,
                unitPrice,
                currency,
                soldAt,
                orderUrl,
                shippingCost,
                fee
        );
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
