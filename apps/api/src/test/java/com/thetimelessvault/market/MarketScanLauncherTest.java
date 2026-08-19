package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketScanLauncherTest {

    @Mock MarketScanService scans;
    @InjectMocks MarketScanLauncher launcher;

    @Test
    void scansEbayThenBrickLinkEvenIfEbayFails() {
        UUID catalogId = UUID.randomUUID();
        doThrow(new RuntimeException("ebay down")).when(scans).scan(catalogId, Platform.EBAY, ScanTrigger.MANUAL);

        launcher.scanBothManual(catalogId);

        verify(scans).scan(catalogId, Platform.EBAY, ScanTrigger.MANUAL);
        verify(scans).scan(catalogId, Platform.BRICKLINK, ScanTrigger.MANUAL);
    }
}
