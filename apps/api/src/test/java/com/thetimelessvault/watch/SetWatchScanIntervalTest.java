package com.thetimelessvault.watch;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetWatchScanIntervalTest {

    @Test
    void dueWhenNeverScanned() {
        SetWatch watch = newWatch(15, 360, null, null);
        Instant now = Instant.parse("2026-08-16T12:00:00Z");
        assertTrue(watch.isEbayDue(now));
        assertTrue(watch.isBrickLinkDue(now));
        assertTrue(watch.isDue(now));
    }

    @Test
    void ebayAndBrickLinkUseSeparateClocks() {
        Instant lastEbay = Instant.parse("2026-08-16T12:00:00Z");
        Instant lastBrickLink = Instant.parse("2026-08-16T12:00:00Z");
        SetWatch watch = newWatch(15, 360, lastEbay, lastBrickLink);
        Instant fourteenMinutes = lastEbay.plusSeconds(14 * 60);
        Instant fifteenMinutes = lastEbay.plusSeconds(15 * 60);
        Instant sixHours = lastBrickLink.plusSeconds(360 * 60);

        assertFalse(watch.isEbayDue(fourteenMinutes));
        assertTrue(watch.isEbayDue(fifteenMinutes));
        assertFalse(watch.isBrickLinkDue(fifteenMinutes));
        assertTrue(watch.isBrickLinkDue(sixHours));
        assertTrue(watch.isDue(fifteenMinutes));
    }

    @Test
    void defaultsToFiveMinutesAndSixHours() {
        SetWatch watch = new SetWatch();
        assertEquals(5, watch.getEbayScanIntervalMinutes());
        assertEquals(360, watch.getBricklinkScanIntervalMinutes());
    }

    @Test
    void allowsFiveMinuteInterval() {
        SetWatch watch = newWatch(5, 5, null, null);
        assertEquals(5, watch.getEbayScanIntervalMinutes());
        assertEquals(5, watch.getBricklinkScanIntervalMinutes());
    }

    @Test
    void clampsBelowFiveMinutes() {
        SetWatch watch = newWatch(1, 1, null, null);
        assertEquals(5, watch.getEbayScanIntervalMinutes());
        assertEquals(5, watch.getBricklinkScanIntervalMinutes());
    }

    @Test
    void disabledWatchIsNeverDue() {
        SetWatch watch = newWatch(15, 360, null, null);
        watch.setEnabled(false);
        Instant now = Instant.parse("2026-08-16T12:00:00Z");
        assertFalse(watch.isEbayDue(now));
        assertFalse(watch.isBrickLinkDue(now));
        assertFalse(watch.isDue(now));
    }

    private static SetWatch newWatch(
            int ebayMinutes,
            int bricklinkMinutes,
            Instant lastEbay,
            Instant lastBrickLink
    ) {
        SetWatch watch = new SetWatch();
        watch.setEnabled(true);
        watch.setEbayScanIntervalMinutes(ebayMinutes);
        watch.setBricklinkScanIntervalMinutes(bricklinkMinutes);
        if (lastEbay != null) {
            watch.recordEbayScan(null, lastEbay);
        }
        if (lastBrickLink != null) {
            watch.recordBrickLinkScan(null, lastBrickLink);
        }
        return watch;
    }
}
