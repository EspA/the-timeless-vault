package com.thetimelessvault.market;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.market", name = "local-schedule", havingValue = "true")
public class MarketScheduler {

    private final MarketScanService marketScanService;

    public MarketScheduler(MarketScanService marketScanService) {
        this.marketScanService = marketScanService;
    }

    @Scheduled(
            initialDelayString = "${app.market.scan-delay-ms:300000}",
            fixedDelayString = "${app.market.scan-delay-ms:300000}"
    )
    public void scan() {
        marketScanService.scanDueWatches();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "America/New_York")
    public void purgeScanLogs() {
        marketScanService.purgeOldScanLogs();
        marketScanService.purgeOldSnapshots();
    }
}
