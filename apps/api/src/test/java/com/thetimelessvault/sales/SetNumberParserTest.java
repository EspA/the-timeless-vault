package com.thetimelessvault.sales;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SetNumberParserTest {

    @Test
    void readsSetNumberFromTtvSku() {
        assertEquals("75192-1", SetNumberParser.fromSku("TTV-75192-1-A3F2"));
    }

    @Test
    void readsSetNumberFromTitleAfterLego() {
        assertEquals("75192", SetNumberParser.fromTitle("LEGO 75192 Star Wars Millennium Falcon (New Sealed In Box)"));
    }

    @Test
    void ignoresBlankValues() {
        assertNull(SetNumberParser.fromSku(" "));
        assertNull(SetNumberParser.fromTitle(null));
        assertEquals("75192-1", SetNumberParser.firstNonBlank(" ", null, "75192-1"));
    }
}
