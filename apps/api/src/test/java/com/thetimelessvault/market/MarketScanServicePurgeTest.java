package com.thetimelessvault.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.thetimelessvault.alerts.ListingAdjustmentService;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketScanServicePurgeTest {

    @Mock ScanLogRepository scanLogs;
    @Mock MarketSnapshotRepository snapshots;
    @Mock ListingAdjustmentService listingAdjustments;
    @Mock ObjectProvider<MarketScanService> self;
    @InjectMocks MarketScanService service;

    @Test
    void purgeDeletesEntriesOlderThanSevenDays() {
        Instant before = Instant.now().minus(MarketScanService.SCAN_LOG_RETENTION).minusSeconds(2);
        when(scanLogs.deleteByScannedAtBefore(any())).thenReturn(12L);

        assertEquals(12L, service.purgeOldScanLogs());

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(scanLogs).deleteByScannedAtBefore(cutoff.capture());
        Instant after = Instant.now().minus(MarketScanService.SCAN_LOG_RETENTION).plusSeconds(2);
        assertTrue(!cutoff.getValue().isBefore(before) && !cutoff.getValue().isAfter(after));
    }

    @Test
    void snapshotPurgeKeepsLatestAndRemovesOlderThanSevenDays() {
        Instant before = Instant.now().minus(MarketScanService.SCAN_LOG_RETENTION).minusSeconds(2);
        when(snapshots.deleteStaleBefore(any())).thenReturn(3);

        assertEquals(3L, service.purgeOldSnapshots());

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(snapshots).deleteStaleBefore(cutoff.capture());
        Instant after = Instant.now().minus(MarketScanService.SCAN_LOG_RETENTION).plusSeconds(2);
        assertTrue(!cutoff.getValue().isBefore(before) && !cutoff.getValue().isAfter(after));
    }

    @Test
    void snapshotPurgeStopsAfterAPartialBatch() {
        when(snapshots.deleteStaleBefore(any()))
                .thenReturn(MarketScanService.SNAPSHOT_PURGE_BATCH)
                .thenReturn(12);

        assertEquals(MarketScanService.SNAPSHOT_PURGE_BATCH + 12L, service.purgeOldSnapshots());
        verify(snapshots, times(2)).deleteStaleBefore(any());
    }
}
