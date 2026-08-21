package com.thetimelessvault.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.sales", name = "local-schedule", havingValue = "true")
public class SalesScheduler {

    private final SalesSyncService salesSyncService;

    public SalesScheduler(SalesSyncService salesSyncService) {
        this.salesSyncService = salesSyncService;
    }

    @Scheduled(
            initialDelayString = "${app.sales.sync-delay-ms:300000}",
            fixedDelayString = "${app.sales.sync-delay-ms:300000}"
    )
    public void sync() {
        salesSyncService.sync();
    }
}
