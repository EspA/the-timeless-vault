package com.thetimelessvault.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeMapperTest {

    @Test
    void mapsKnownThemes() {
        assertEquals("Star Wars", ThemeMapper.ebayStoreCategory("Star Wars"));
        assertEquals("Marvel", ThemeMapper.ebayStoreCategory("Marvel Super Heroes"));
        assertEquals("The Lord of the Rings", ThemeMapper.ebayStoreCategory("The Hobbit"));
        assertEquals("Other", ThemeMapper.ebayStoreCategory("City"));
    }

    @Test
    void buildsSealedTitle() {
        assertEquals(
                "LEGO 10236 Star Wars Ewok Village (New Sealed In Box)",
                ThemeMapper.suggestedTitle("Star Wars", "10236-1", "Ewok Village", ItemCondition.NEW_SEALED)
        );
        assertEquals(
                "LEGO 10237 The Lord of the Rings The Two Towers Tower of Orthanc (New Sealed In Box)".substring(0, ThemeMapper.TITLE_MAX_LENGTH),
                ThemeMapper.suggestedTitle(
                        "The Lord of the Rings",
                        "The Two Towers",
                        "10237-1",
                        "Tower of Orthanc",
                        ItemCondition.NEW_SEALED
                )
        );
        String limited = ThemeMapper.suggestedTitle(
                "Star Wars",
                "10236-1",
                "Super Extraordinarily Long Collectors Edition Display Set Name",
                ItemCondition.NEW_SEALED
        );
        assertEquals(ThemeMapper.TITLE_MAX_LENGTH, limited.length());
        assertEquals(
                "LEGO 10236 Ewok Village",
                ThemeMapper.suggestedEbaySearch("Star Wars", "10236-1", "Ewok Village")
        );
        assertEquals(
                "LEGO 79001 Escape from Mirkwood Spiders",
                ThemeMapper.suggestedEbaySearch("The Hobbit", "79001-1", "Escape from Mirkwood Spiders")
        );
        assertEquals(
                "LEGO 79001 Escape from Mirkwood Spiders",
                ThemeMapper.suggestedEbaySearch("The Hobbit", "79001-2", "Escape from Mirkwood Spiders")
        );
    }

    @Test
    void stripsVariantSuffixFromSetNumber() {
        assertEquals("79001", ThemeMapper.displaySetNumber("79001-1"));
        assertEquals("79001", ThemeMapper.displaySetNumber("79001-2"));
        assertEquals("10236", ThemeMapper.displaySetNumber("10236-1"));
    }
}
