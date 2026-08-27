package com.thetimelessvault.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class QuoteDtos {

    private QuoteDtos() {
    }

    public record LineView(
            UUID id,
            UUID catalogItemId,
            String setNumber,
            String title,
            BigDecimal cost,
            BigDecimal proratedShipping,
            BigDecimal costWithShipping,
            BigDecimal medianMarketPrice,
            BigDecimal marginPercent,
            BigDecimal marginDollars,
            String marginTone
    ) {
    }

    public record QuoteView(
            UUID id,
            String number,
            QuoteStatus status,
            String description,
            int lineCount,
            BigDecimal shippingTotal,
            BigDecimal totalCost,
            BigDecimal totalProratedShipping,
            BigDecimal totalCostWithShipping,
            BigDecimal totalMedianMarketPrice,
            BigDecimal averageMarginPercent,
            String averageMarginTone,
            BigDecimal totalMarginDollars,
            UUID purchaseOrderId,
            String purchaseOrderNumber,
            List<LineView> lines,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record QuotePage(
            List<QuoteView> items,
            int page,
            int size,
            long total,
            int totalPages
    ) {
    }
}
