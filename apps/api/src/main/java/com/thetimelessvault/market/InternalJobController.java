package com.thetimelessvault.market;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final MarketScanService marketScanService;

    public InternalJobController(MarketScanService marketScanService) {
        this.marketScanService = marketScanService;
    }

    @PostMapping("/market-scan")
    public Map<String, String> marketScan() {
        marketScanService.scanDueWatches();
        return Map.of("status", "ok");
    }
}
