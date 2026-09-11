package com.thetimelessvault.inventory;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.DescriptionHtml;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

final class InventoryCsv {

    static final List<String> COLUMNS = List.of(
            "Id",
            "SKU",
            "Set id",
            "Set number",
            "Title",
            "Short description",
            "Description",
            "Box grade",
            "Condition",
            "Item type",
            "Status",
            "Qty",
            "Cost",
            "Price",
            "eBay price",
            "Shopify price",
            "BrickLink price",
            "BrickOwl price",
            "Minimum offer",
            "Shopify URL"
    );

    static final String HEADER = String.join(",", COLUMNS);

    private InventoryCsv() {
    }

    static String render(List<InventoryItem> items) {
        return render(items, Map.of());
    }

    static String render(List<InventoryItem> items, Map<UUID, String> shopifyUrls) {
        StringBuilder out = new StringBuilder();
        out.append('\uFEFF');
        out.append(HEADER).append('\n');
        Map<UUID, String> urls = shopifyUrls == null ? Map.of() : shopifyUrls;
        for (InventoryItem item : items) {
            List<String> values = cells(item, urls.get(item.getId()));
            if (values.size() != COLUMNS.size()) {
                throw new IllegalStateException("CSV column count mismatch");
            }
            out.append(values.stream().map(InventoryCsv::cell).collect(Collectors.joining(","))).append('\n');
        }
        return out.toString();
    }

    private static List<String> cells(InventoryItem item, String shopifyUrl) {
        CatalogItem catalog = item.getCatalogItem();
        Integer boxGrade = DescriptionHtml.inferBoxGradeScore(item.getDescription());
        List<String> values = new ArrayList<>();
        values.add(text(item.getId()));
        values.add(item.getSku());
        values.add(setId(catalog));
        values.add(catalog == null ? "" : catalog.getSetNumber());
        values.add(item.getTitle());
        values.add(item.getShortDescription());
        values.add(DescriptionHtml.toPlainText(item.getDescription()));
        values.add(boxGrade == null ? "" : String.valueOf(boxGrade));
        values.add(item.getCondition() == null ? "" : item.getCondition().name());
        values.add(item.getItemType() == null ? "" : item.getItemType().name());
        values.add(item.getStockStatus() == null ? "" : item.getStockStatus().name());
        values.add(String.valueOf(item.getQuantity()));
        values.add(money(item.getCost()));
        values.add(money(item.getPrice()));
        values.add(money(item.getEbayPrice()));
        values.add(money(item.getShopifyPrice()));
        values.add(money(item.getBricklinkPrice()));
        values.add(money(item.getBrickowlPrice()));
        values.add(money(item.getMinimumOffer()));
        values.add(shopifyUrl);
        return values;
    }

    private static String setId(CatalogItem catalog) {
        if (catalog == null || catalog.getSetNumber() == null) {
            return "";
        }
        return catalog.getSetNumber().replaceFirst("-1$", "");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean quoted = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!quoted) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
