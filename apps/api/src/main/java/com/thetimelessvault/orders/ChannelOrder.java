package com.thetimelessvault.orders;

import com.thetimelessvault.common.Platform;

import java.math.BigDecimal;
import java.time.Instant;

public record ChannelOrder(
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
        BigDecimal platformFee,
        OrderStatus status,
        String trackingNumber,
        String shippingProvider
) {
    public ChannelOrder {
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
        status = status == null ? OrderStatus.OPEN : status;
        trackingNumber = blankToNull(trackingNumber);
        shippingProvider = ShippingProviders.infer(trackingNumber, blankToNull(shippingProvider));
    }

    public String identityLineId() {
        return lineId == null || lineId.isBlank() ? "0" : lineId;
    }

    public ChannelOrder withPlatformFee(BigDecimal fee) {
        return new ChannelOrder(
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
                fee,
                status,
                trackingNumber,
                shippingProvider
        );
    }

    public ChannelOrder withFulfillment(OrderStatus nextStatus, String tracking, String provider) {
        return new ChannelOrder(
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
                platformFee,
                nextStatus == null ? status : nextStatus,
                tracking == null ? trackingNumber : tracking,
                provider == null ? shippingProvider : provider
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
