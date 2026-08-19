package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MarketScanLauncher {

    private static final Logger log = LoggerFactory.getLogger(MarketScanLauncher.class);

    private final MarketScanService scans;

    public MarketScanLauncher(MarketScanService scans) {
        this.scans = scans;
    }

    @Async
    public void scanBothManual(UUID catalogId) {
        scanQuietly(catalogId, Platform.EBAY);
        scanQuietly(catalogId, Platform.BRICKLINK);
    }

    private void scanQuietly(UUID catalogId, Platform platform) {
        try {
            scans.scan(catalogId, platform, ScanTrigger.MANUAL);
        } catch (Exception e) {
            log.warn("{} market scan failed for catalog {}", platform, catalogId, e);
        }
    }
}
