package com.thetimelessvault.ledger;

import java.math.BigDecimal;

public record LedgerSale(
        String kind,
        String itemNumber,
        String name,
        String theme,
        String subtheme,
        Integer year,
        String currency,
        BigDecimal salePriceTotal,
        BigDecimal salePriceUnit,
        BigDecimal salePriceShipping,
        BigDecimal salePriceFees,
        int saleQuantity,
        String saleCondition,
        String saleDate,
        String buyDate,
        String buyCondition,
        BigDecimal buyPrice,
        BigDecimal profit
) {
}
