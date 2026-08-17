package com.thetimelessvault.ebay;

import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.inventory.InventoryItem;

import java.util.ArrayList;
import java.util.List;

public record EbayCatalogPreview(
        String setNumber,
        String title,
        String condition,
        boolean catalogMatch,
        String source,
        String summary,
        String epid,
        String brand,
        String mpn,
        List<String> upc,
        List<String> ean,
        List<Aspect> aspects
) {
    public record Aspect(String name, List<String> values) {
    }

    public static EbayCatalogPreview from(InventoryItem item, EbayCatalogTemplate template) {
        boolean catalogMatch = template.epid() != null && !template.epid().isBlank();
        String setNumber = ThemeMapper.displaySetNumber(item.getCatalogItem().getSetNumber());
        String source = catalogMatch ? "EBAY_CATALOG" : "BRICKECONOMY";
        String summary = catalogMatch
                ? "Matched eBay catalog product ePID " + template.epid() + ". Item specifics such as Age Level and Interests come from that product."
                : "No matching eBay catalog product was found. These fields come from BrickEconomy and your item condition.";
        List<Aspect> aspects = new ArrayList<>();
        template.aspects().forEach((name, values) -> aspects.add(new Aspect(name, values)));
        return new EbayCatalogPreview(
                setNumber,
                item.getTitle(),
                item.getCondition() == null ? null : item.getCondition().name(),
                catalogMatch,
                source,
                summary,
                template.epid(),
                "LEGO",
                template.mpn(),
                template.upc(),
                template.ean(),
                aspects
        );
    }
}
