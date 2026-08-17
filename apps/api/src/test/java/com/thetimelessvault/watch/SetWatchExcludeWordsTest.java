package com.thetimelessvault.watch;

import com.thetimelessvault.ebay.EbayMarketFilters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetWatchExcludeWordsTest {

    @Test
    void createUsesDefaultsWhenOmitted() {
        assertEquals(
                EbayMarketFilters.DEFAULT_EXCLUDE_WORDS,
                SetWatchService.resolveExcludeWords(null, null, true, EbayMarketFilters.DEFAULT_EXCLUDE_WORDS)
        );
    }

    @Test
    void createUsesSettingsDefaultsWhenOmitted() {
        assertEquals(
                "-custom -moc",
                SetWatchService.resolveExcludeWords(null, null, true, "-custom -moc")
        );
    }

    @Test
    void createKeepsClearedList() {
        assertEquals("", SetWatchService.resolveExcludeWords("", null, true, EbayMarketFilters.DEFAULT_EXCLUDE_WORDS));
        assertEquals("-yellow", SetWatchService.resolveExcludeWords(" -yellow ", null, true, EbayMarketFilters.DEFAULT_EXCLUDE_WORDS));
    }

    @Test
    void updatePersistsClearedOrEditedList() {
        assertEquals("", SetWatchService.resolveExcludeWords("", EbayMarketFilters.DEFAULT_EXCLUDE_WORDS, false, EbayMarketFilters.DEFAULT_EXCLUDE_WORDS));
        assertEquals("-box", SetWatchService.resolveExcludeWords("-box", EbayMarketFilters.DEFAULT_EXCLUDE_WORDS, false, EbayMarketFilters.DEFAULT_EXCLUDE_WORDS));
    }

    @Test
    void updateKeepsExistingWhenFieldOmitted() {
        assertEquals(
                EbayMarketFilters.DEFAULT_EXCLUDE_WORDS,
                SetWatchService.resolveExcludeWords(null, EbayMarketFilters.DEFAULT_EXCLUDE_WORDS, false, "-newer")
        );
    }
}
