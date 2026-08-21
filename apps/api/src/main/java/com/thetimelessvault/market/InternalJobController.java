package com.thetimelessvault.market;

import com.thetimelessvault.sales.SalesSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final MarketScanService marketScanService;
    private final SalesSyncService salesSyncService;

    public InternalJobController(MarketScanService marketScanService, SalesSyncService salesSyncService) {
        this.marketScanService = marketScanService;
        this.salesSyncService = salesSyncService;
    }

    @PostMapping("/market-scan")
    public Map<String, String> marketScan() {
        marketScanService.scanDueWatches();
        return Map.of("status", "ok");
    }

    @PostMapping("/sales-sync")
    public Map<String, Object> salesSync() {
        return salesSyncService.sync();
    }
}
