package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayMarketFiltersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsNorthAmericaNewSellers() throws Exception {
        var item = mapper.readTree("""
                {
                  "itemLocation": { "country": "US" },
                  "seller": { "username": "brickshop", "feedbackScore": 12 }
                }
                """);
        assertTrue(EbayMarketFilters.matchesWatch(item, 1, null));
        assertTrue(EbayMarketFilters.locatedInNorthAmerica(mapper.readTree(
                "{ \"itemLocation\": { \"country\": \"CA\" } }")));
        assertTrue(EbayMarketFilters.locatedInNorthAmerica(mapper.readTree(
                "{ \"itemLocation\": { \"country\": \"MX\" } }")));
    }

    @Test
    void rejectsOverseasOrLowFeedback() throws Exception {
        assertFalse(EbayMarketFilters.matchesWatch(mapper.readTree("""
                {
                  "itemLocation": { "country": "DE" },
                  "seller": { "feedbackScore": 99 }
                }
                """), 1, null));
        assertFalse(EbayMarketFilters.matchesWatch(mapper.readTree("""
                {
                  "itemLocation": { "country": "US" },
                  "seller": { "feedbackScore": 0 }
                }
                """), 1, null));
        assertTrue(EbayMarketFilters.locatedInNorthAmerica(mapper.readTree("{ \"itemLocation\": {} }")));
        assertTrue(EbayMarketFilters.locatedInNorthAmerica(mapper.readTree(
                "{ \"listingMarketplaceId\": \"EBAY_US\" }")));
    }

    @Test
    void excludesWordsFromTitle() throws Exception {
        var item = mapper.readTree("""
                {
                  "title": "LEGO 10143 Death Star II instructions only",
                  "itemLocation": { "country": "US" },
                  "seller": { "feedbackScore": 12 }
                }
                """);
        assertFalse(EbayMarketFilters.matchesWatch(item, 1, "instructions, part out"));
        assertFalse(EbayMarketFilters.matchesWatch(item, 1, "-instructions -minifig"));
        assertTrue(EbayMarketFilters.matchesWatch(item, 1, "minifig"));
    }

    @Test
    void stripsParenthesesThatBreakBrowseSearch() {
        assertEquals(
                "LEGO 10143 Star Wars Death Star II",
                EbayMarketFilters.browseQuery("LEGO 10143 Star Wars Death Star II (New Sealed In Box)")
        );
    }

    @Test
    void searchAndExcludeWordsApplyToTitleOnly() throws Exception {
        var descriptionOnly = mapper.readTree("""
                {
                  "title": "LEGO 10143 NISB",
                  "shortDescription": "Star Wars Death Star II with extra minifigures",
                  "itemLocation": { "country": "US" },
                  "seller": { "feedbackScore": 12 }
                }
                """);
        assertFalse(EbayMarketFilters.matchesWatch(
                descriptionOnly, 1, "LEGO 10143 Star Wars Death Star II", "-minifigures"));
        assertTrue(EbayMarketFilters.matchesWatch(
                descriptionOnly, 1, "LEGO 10143", "-minifigures"));

        var titleMatch = mapper.readTree("""
                {
                  "title": "LEGO 10143 Star Wars Death Star II NISB",
                  "shortDescription": "Includes extra minifigures and display instructions",
                  "itemLocation": { "country": "US" },
                  "seller": { "feedbackScore": 12 }
                }
                """);
        assertTrue(EbayMarketFilters.matchesWatch(
                titleMatch, 1, "LEGO 10143 Star Wars Death Star II", "-minifigures -instructions"));
        assertFalse(EbayMarketFilters.matchesWatch(
                titleMatch, 1, "LEGO 10143 Star Wars Death Star II", "-nisb"));
    }

    @Test
    void doesNotSendExcludeWordsToEbaySearch() {
        assertEquals(
                "LEGO 10143 Death Star II",
                EbayMarketFilters.browseQuery("LEGO 10143 Death Star II", "-yellow -box")
        );
        assertEquals(
                List.of("yellow", "box"),
                EbayMarketFilters.parseExcludeWords("-yellow -box")
        );
    }

    @Test
    void sealedHobbitSetIsNotDroppedByLedOrTheOrHyphen() throws Exception {
        var item = mapper.readTree("""
                {
                  "title": "LEGO 79015 Witch-king Battle The Hobbit New in Box Sealed Retired Set",
                  "itemLocation": { "country": "US" },
                  "seller": { "feedbackScore": 12 }
                }
                """);
        assertTrue(EbayMarketFilters.matchesWatch(
                item,
                1,
                "LEGO 79015 The Hobbit Witch-King Battle",
                EbayMarketFilters.DEFAULT_EXCLUDE_WORDS
        ));
        assertTrue(EbayMarketFilters.titleExcludes("LEGO 79015 New Sealed", "-led"));
        assertFalse(EbayMarketFilters.titleExcludes("LEGO 79015 LED kit", "-led"));
    }

    @Test
    void asterisksInTitleAreNotTreatAsExcludeOrSearchWildcards() throws Exception {
        var item = mapper.readTree("""
                {
                  "title": "Lego 79015 The Hobbit Witch-King Battle *New Sealed*",
                  "itemLocation": { "country": "US" },
                  "seller": { "feedbackScore": 12 }
                }
                """);
        assertTrue(EbayMarketFilters.matchesWatch(
                item,
                1,
                "LEGO 79015 The Hobbit Witch-King Battle",
                EbayMarketFilters.DEFAULT_EXCLUDE_WORDS
        ));
        assertTrue(EbayMarketFilters.titleMatchesSearch(
                "Lego 79015 The Hobbit Witch*King Battle **NEW**",
                "LEGO 79015 The Hobbit Witch-King Battle"
        ));
        assertTrue(EbayMarketFilters.titleExcludes(
                "Lego 79015 The Hobbit Witch-King Battle *New Sealed*",
                EbayMarketFilters.DEFAULT_EXCLUDE_WORDS
        ));
    }

    @Test
    void usesDefaultExcludeWordsOnlyWhenUnset() {
        assertEquals(EbayMarketFilters.DEFAULT_EXCLUDE_WORDS, EbayMarketFilters.effectiveExcludeWords(null));
        assertEquals("", EbayMarketFilters.effectiveExcludeWords(""));
        assertEquals("  ", EbayMarketFilters.effectiveExcludeWords("  "));
        assertEquals("-yellow -box", EbayMarketFilters.effectiveExcludeWords("-yellow -box"));
        assertTrue(EbayMarketFilters.DEFAULT_EXCLUDE_WORDS.contains("-minifig"));
        assertTrue(EbayMarketFilters.DEFAULT_EXCLUDE_WORDS.contains("-replica"));
    }
}
