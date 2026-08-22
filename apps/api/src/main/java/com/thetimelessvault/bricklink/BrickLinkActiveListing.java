package com.thetimelessvault.bricklink;

import java.math.BigDecimal;

public record BrickLinkActiveListing(
        String inventoryId,
        String setNumber,
        String title,
        int quantity,
        BigDecimal unitPrice,
        String newOrUsed,
        String completeness,
        String remarks
) {
}
