package com.thetimelessvault.market;

import com.thetimelessvault.bricklink.BrickLinkListingImportService;
import com.thetimelessvault.ebay.EbayListingImportService;
import com.thetimelessvault.sales.SalesSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final MarketScanService marketScanService;
    private final SalesSyncService salesSyncService;
    private final EbayListingImportService ebayListingImport;
    private final BrickLinkListingImportService brickLinkListingImport;

    public InternalJobController(
            MarketScanService marketScanService,
            SalesSyncService salesSyncService,
            EbayListingImportService ebayListingImport,
            BrickLinkListingImportService brickLinkListingImport
    ) {
        this.marketScanService = marketScanService;
        this.salesSyncService = salesSyncService;
        this.ebayListingImport = ebayListingImport;
        this.brickLinkListingImport = brickLinkListingImport;
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

    @PostMapping("/ebay-listing-import")
    public EbayListingImportService.Report ebayListingImport(
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return ebayListingImport.run(dryRun);
    }

    @PostMapping("/bricklink-listing-import")
    public BrickLinkListingImportService.Report brickLinkListingImport(
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return brickLinkListingImport.run(dryRun);
    }
}
