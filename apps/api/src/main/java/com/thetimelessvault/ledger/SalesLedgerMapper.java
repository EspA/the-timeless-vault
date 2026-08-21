package com.thetimelessvault.ledger;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SalesLedgerMapper {

    private SalesLedgerMapper() {
    }

    public static List<LedgerSale> from(JsonNode data) {
        List<LedgerSale> sales = new ArrayList<>();
        if (data == null || data.isMissingNode() || data.isNull()) {
            return sales;
        }
        addAll(sales, data.path("set_sales"), "SET");
        addAll(sales, data.path("minifig_sales"), "MINIFIG");
        sales.sort(Comparator
                .comparing(LedgerSale::saleDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LedgerSale::itemNumber, Comparator.nullsLast(String::compareToIgnoreCase)));
        return sales;
    }

    private static void addAll(List<LedgerSale> sales, JsonNode nodes, String kind) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            BigDecimal unit = money(node, "sale_price_unit");
            BigDecimal fees = money(node, "sale_price_fees");
            BigDecimal buy = money(node, "buy_price");
            sales.add(new LedgerSale(
                    kind,
                    firstText(node, kind.equals("SET") ? "set_number" : "minifig_number"),
                    firstText(node, "name"),
                    firstText(node, "theme"),
                    firstText(node, "subtheme"),
                    integer(node, "year"),
                    firstText(node, "currency") == null ? "USD" : firstText(node, "currency"),
                    money(node, "sale_price_total"),
                    unit,
                    money(node, "sale_price_shipping"),
                    fees,
                    Math.max(1, node.path("sale_quantity").asInt(1)),
                    firstText(node, "sale_condition"),
                    firstText(node, "sale_date"),
                    firstText(node, "buy_date"),
                    firstText(node, "buy_condition"),
                    buy,
                    profit(unit, fees, buy)
            ));
        }
    }

    static BigDecimal profit(BigDecimal salePriceUnit, BigDecimal salePriceFees, BigDecimal buyPrice) {
        return salePriceUnit
                .subtract(salePriceFees.add(buyPrice))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || value.asText("").isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(value.asText().trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.canConvertToInt()) {
            return null;
        }
        return value.asInt();
    }

    private static String firstText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }
}
