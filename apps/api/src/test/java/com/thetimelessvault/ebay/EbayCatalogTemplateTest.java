package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.inventory.InventoryItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayCatalogTemplateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsSetNumberListingsAndSkipsVarietyLots() {
        assertTrue(EbayCatalogTemplate.isTemplateCandidate(
                "LEGO Speed Champions 75870 Chevrolet Corvette Z06 New Sealed", "75870"));
        assertFalse(EbayCatalogTemplate.isTemplateCandidate(
                "LEGO Speed Champions – Choose Your Set – Brand New Sealed", "75870"));
        assertFalse(EbayCatalogTemplate.isTemplateCandidate(
                "LEGO 758700 Chevrolet Corvette", "75870"));
    }

    @Test
    void catalogFallbackFillsBrandThemeAndIdentifiers() {
        InventoryItem item = sealedCorvette();
        EbayCatalogTemplate template = EbayCatalogTemplate.fromCatalog(item);

        assertEquals("75870", template.mpn());
        assertEquals(List.of("673419247252"), template.upc());
        assertEquals(List.of("LEGO"), template.aspects().get("Brand"));
        assertEquals(List.of("Speed Champions"), template.aspects().get("LEGO Theme"));
        assertEquals(List.of("75870"), template.aspects().get("LEGO Set Number"));
        assertEquals(List.of("Complete Set"), template.aspects().get("Type"));
        assertEquals(List.of("Plastic"), template.aspects().get("Material"));
        assertEquals(List.of("Box"), template.aspects().get("Packaging"));
        assertEquals(List.of("Yes"), template.aspects().get("Retired"));
        assertEquals(List.of("2017"), template.aspects().get("Year Retired"));
        assertNull(template.epid());
        assertFalse(template.aspects().containsKey("EAN"));
        assertTrue(template.ean().isEmpty());
    }

    @Test
    void usesEpidFromSearchWhenListingDetailsAreMissing() throws Exception {
        InventoryItem item = sealedCorvette();
        JsonNode summaries = mapper.readTree("""
                [
                  {"itemId": "a", "title": "LEGO 75870 Corvette", "epid": "12053416333"},
                  {"itemId": "b", "title": "LEGO 75870 Chevrolet", "epid": "12053416333"}
                ]
                """);
        List<JsonNode> list = new java.util.ArrayList<>();
        summaries.forEach(list::add);

        EbayCatalogTemplate merged = EbayCatalogTemplate.merge(
                EbayCatalogTemplate.fromCatalog(item),
                list,
                List.of()
        );

        assertEquals("12053416333", merged.epid());
        assertEquals(List.of("Complete Set"), merged.aspects().get("Type"));
        EbayCatalogTemplate slim = merged.withoutCopiedAspects();
        assertEquals("12053416333", slim.epid());
        assertEquals(List.of("Complete Set"), slim.aspects().get("Type"));
        assertEquals(List.of("Plastic"), slim.aspects().get("Material"));
        assertEquals(List.of("Box"), slim.aspects().get("Packaging"));
        assertFalse(slim.aspects().containsKey("Age Level"));
    }

    @Test
    void mergesRicherListingAspectsAndKeepsOurType() throws Exception {
        InventoryItem item = sealedCorvette();
        JsonNode upcOnly = mapper.readTree("""
                {
                  "epid": "12053416333",
                  "mpn": "75870",
                  "gtin": "0673419247252, 5702015591508",
                  "localizedAspects": [
                    {"name": "UPC", "value": "0673419247252"},
                    {"name": "Brand", "value": "LEGO"},
                    {"name": "LEGO Theme", "value": "SPEED CHAMPIONS"},
                    {"name": "Packaging", "value": "Without Packaging"},
                    {"name": "Item Height", "value": "2.4 in"},
                    {"name": "Type", "value": "Incomplete Set"}
                  ]
                }
                """);
        JsonNode specifics = mapper.readTree("""
                {
                  "epid": "12053416333",
                  "mpn": "75870",
                  "localizedAspects": [
                    {"name": "Age Level", "value": "7-14"},
                    {"name": "Interests", "value": "Cars, Vehicles"},
                    {"name": "Type", "value": "Complete Set"},
                    {"name": "LEGO Set Name", "value": "Chevrolet Corvette Z06"},
                    {"name": "Number of Pieces", "value": "173"}
                  ]
                }
                """);

        EbayCatalogTemplate merged = EbayCatalogTemplate.merge(
                EbayCatalogTemplate.fromCatalog(item),
                List.of(upcOnly, specifics)
        );

        assertEquals("12053416333", merged.epid());
        assertEquals("75870", merged.mpn());
        assertTrue(merged.upc().contains("673419247252"));
        assertTrue(merged.ean().contains("5702015591508"));
        assertEquals(List.of("7-14"), merged.aspects().get("Age Level"));
        assertEquals(List.of("Cars, Vehicles"), merged.aspects().get("Interests"));
        assertEquals(List.of("SPEED CHAMPIONS"), merged.aspects().get("LEGO Theme"));
        assertEquals(List.of("Complete Set"), merged.aspects().get("Type"));
        assertEquals(List.of("Plastic"), merged.aspects().get("Material"));
        assertEquals(List.of("Box"), merged.aspects().get("Packaging"));
        assertFalse(merged.aspects().containsKey("Item Height"));
    }

    @Test
    void previewShowsCatalogMatchAndLoadedAspects() {
        InventoryItem item = sealedCorvette();
        EbayCatalogTemplate template = new EbayCatalogTemplate(
                "12053416333",
                "75870",
                List.of("673419247252"),
                List.of(),
                java.util.Map.of(
                        "Brand", List.of("LEGO"),
                        "Age Level", List.of("7-14"),
                        "Interests", List.of("Cars, Vehicles"),
                        "Type", List.of("Complete Set")
                )
        );

        EbayCatalogPreview preview = EbayCatalogPreview.from(item, template);

        assertTrue(preview.catalogMatch());
        assertEquals("EBAY_CATALOG", preview.source());
        assertEquals("12053416333", preview.epid());
        assertEquals("75870", preview.mpn());
        assertTrue(preview.aspects().stream().anyMatch(aspect -> "Age Level".equals(aspect.name())));
        assertTrue(preview.summary().contains("12053416333"));
    }

    @Test
    void previewFallsBackToBrickEconomyWhenNoEpid() {
        InventoryItem item = sealedCorvette();
        EbayCatalogPreview preview = EbayCatalogPreview.from(item, EbayCatalogTemplate.fromCatalog(item));

        assertFalse(preview.catalogMatch());
        assertEquals("BRICKECONOMY", preview.source());
        assertNull(preview.epid());
        assertTrue(preview.summary().contains("BrickEconomy"));
    }

    @Test
    void matchingSummariesRequireTheSetNumber() throws Exception {
        CatalogItem catalog = CatalogItem.create("75870-1");
        JsonNode search = mapper.readTree("""
                {
                  "itemSummaries": [
                    {"itemId": "a", "title": "LEGO 75870 Chevrolet Corvette Z06", "epid": "1"},
                    {"itemId": "b", "title": "LEGO Speed Champions Variety Unopened", "epid": "1"},
                    {"itemId": "c", "title": "LEGO 75871 Ford Mustang GT", "epid": "2"}
                  ]
                }
                """);
        List<JsonNode> matched = EbayCatalogTemplate.matchingSummaries(search, catalog);
        assertEquals(1, matched.size());
        assertEquals("a", matched.get(0).path("itemId").asText());
    }

    @Test
    void fillsYearRetiredFromBrickEconomyWhenEbayOmitsIt() throws Exception {
        InventoryItem item = sealedCorvette();
        JsonNode listing = mapper.readTree("""
                {
                  "epid": "12053416333",
                  "localizedAspects": [
                    {"name": "Brand", "value": "LEGO"},
                    {"name": "Age Level", "value": "7-14"}
                  ]
                }
                """);

        EbayCatalogTemplate merged = EbayCatalogTemplate.merge(
                EbayCatalogTemplate.fromCatalog(item),
                List.of(listing)
        );

        assertEquals(List.of("2017"), merged.aspects().get("Year Retired"));
        assertEquals(List.of("Yes"), merged.aspects().get("Retired"));
        assertEquals(List.of("173"), merged.aspects().get("Number of Pieces"));
    }

    @Test
    void keepsEbayYearRetiredOverBrickEconomy() throws Exception {
        InventoryItem item = sealedCorvette();
        JsonNode listing = mapper.readTree("""
                {
                  "epid": "12053416333",
                  "localizedAspects": [
                    {"name": "Year Retired", "value": "2018"}
                  ]
                }
                """);

        EbayCatalogTemplate merged = EbayCatalogTemplate.merge(
                EbayCatalogTemplate.fromCatalog(item),
                List.of(listing)
        );

        assertEquals(List.of("2018"), merged.aspects().get("Year Retired"));
    }

    @Test
    void keepsCatalogMaterialWhenPresent() throws Exception {
        InventoryItem item = sealedCorvette();
        JsonNode listing = mapper.readTree("""
                {
                  "epid": "12053416333",
                  "localizedAspects": [
                    {"name": "Material", "value": "ABS"}
                  ]
                }
                """);

        EbayCatalogTemplate merged = EbayCatalogTemplate.merge(
                EbayCatalogTemplate.fromCatalog(item),
                List.of(listing)
        );

        assertEquals(List.of("ABS"), merged.aspects().get("Material"));
        assertEquals(List.of("Box"), merged.aspects().get("Packaging"));
    }

    @Test
    void usedItemStillDefaultsMaterialAndPackagingWhenSkippingCatalog() {
        InventoryItem item = sealedCorvette();
        item.setCondition(ItemCondition.USED_COMPLETE);
        EbayCatalogTemplate template = EbayCatalogTemplate.fromCatalog(item);

        assertEquals(List.of("Plastic"), template.aspects().get("Material"));
        assertEquals(List.of("Box"), template.aspects().get("Packaging"));
        assertEquals(List.of("Plastic"), template.withoutCopiedAspects().aspects().get("Material"));
        assertEquals(List.of("Box"), template.withoutCopiedAspects().aspects().get("Packaging"));
    }

    private static InventoryItem sealedCorvette() {
        CatalogItem catalog = CatalogItem.create("75870-1");
        catalog.setName("Chevrolet Corvette Z06");
        catalog.setTheme("Speed Champions");
        catalog.setUpc("673419247252");
        catalog.setEan("5057065404347");
        catalog.setYear(2016);
        catalog.setPiecesCount(173);
        catalog.setRetired(true);
        catalog.setRetiredDate(java.time.LocalDate.of(2017, 12, 31));
        catalog.setReleasedDate(java.time.LocalDate.of(2016, 1, 1));
        InventoryItem item = InventoryItem.create(catalog, "SKU-75870");
        item.setCondition(ItemCondition.NEW_SEALED);
        item.setTitle("LEGO 75870 Speed Champions Chevrolet Corvette Z06 (New Sealed In Box)");
        return item;
    }
}
