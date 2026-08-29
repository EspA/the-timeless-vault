package com.thetimelessvault.common;

import com.thetimelessvault.catalog.CatalogItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultListingCopyTest {

    @Test
    void sealedDescriptionUsesNisbAndMintBoxGrade() {
        String html = DefaultListingCopy.description(catalog());

        assertTrue(html.contains("New Sealed In Box (NISB)"));
        assertTrue(html.contains("Factory seals intact. Never opened."));
        assertTrue(html.contains("Box Grade:</strong> 10/10 (Collector Grade):"));
        assertTrue(html.contains("the box is in mint condition"));
    }

    @Test
    void conditionParagraphsMatchListingCopy() {
        assertTrue(DefaultListingCopy.conditionParagraph(ItemCondition.NEW_SEALED)
                .contains("New Sealed In Box (NISB)"));
        assertTrue(DefaultListingCopy.conditionParagraph(ItemCondition.NEW_COMPLETE)
                .contains("New Open Box (NOB)"));
        assertTrue(DefaultListingCopy.conditionParagraph(ItemCondition.NEW_COMPLETE)
                .contains("All bags sealed with instructions."));
        assertTrue(DefaultListingCopy.conditionParagraph(ItemCondition.NEW_INCOMPLETE)
                .contains("Some bags or instructions missing"));
        assertTrue(DefaultListingCopy.conditionParagraph(ItemCondition.NEW_OTHER)
                .contains("new other"));
        assertTrue(DefaultListingCopy.conditionParagraph(ItemCondition.USED_COMPLETE)
                .contains("Used 100% Complete:"));
        assertTrue(DefaultListingCopy.conditionParagraph(ItemCondition.USED_INCOMPLETE)
                .contains("Used Missing Parts:"));
    }

    @Test
    void usedConditionStillProducesShortDescription() {
        String html = DefaultListingCopy.description(catalog(), ItemCondition.USED_COMPLETE, BoxGrade.GRADE_8);
        String shortDescription = DescriptionHtml.shortDescriptionFromListingHtml(html);

        assertTrue(shortDescription.startsWith("Used 100% Complete:"));
        assertTrue(shortDescription.contains("Box Grade: 8/10 (Excellent):"));
        assertTrue(shortDescription.length() <= 255);
        assertFalse(shortDescription.contains("Set number"));
    }

    private static CatalogItem catalog() {
        CatalogItem catalog = CatalogItem.create("75192-1");
        catalog.setName("Millennium Falcon");
        catalog.setPiecesCount(7541);
        catalog.setMinifigsCount(8);
        return catalog;
    }
}
