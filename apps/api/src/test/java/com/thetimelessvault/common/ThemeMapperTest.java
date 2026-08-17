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
                "LEGO 10236 Star Wars Ewok Village",
                ThemeMapper.suggestedEbaySearch("Star Wars", "10236-1", "Ewok Village")
        );
    }
}
