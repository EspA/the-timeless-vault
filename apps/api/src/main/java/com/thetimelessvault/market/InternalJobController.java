package com.thetimelessvault.market;

import com.thetimelessvault.bricklink.BrickLinkListingImportService;
import com.thetimelessvault.ebay.EbayListingImportService;
import com.thetimelessvault.inventory.DescriptionBackfillService;
import com.thetimelessvault.inventory.PriceBackfillService;
import com.thetimelessvault.orders.OrderSyncService;
import com.thetimelessvault.shopify.ShopifyListingImportService;
import com.thetimelessvault.shopify.ShopifySkuBackfillService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final MarketScanService marketScanService;
    private final OrderSyncService orderSyncService;
    private final EbayListingImportService ebayListingImport;
    private final BrickLinkListingImportService brickLinkListingImport;
    private final ShopifyListingImportService shopifyListingImport;
    private final DescriptionBackfillService descriptionBackfill;
    private final PriceBackfillService priceBackfill;
    private final ShopifySkuBackfillService shopifySkuBackfill;

    public InternalJobController(
            MarketScanService marketScanService,
            OrderSyncService orderSyncService,
            EbayListingImportService ebayListingImport,
            BrickLinkListingImportService brickLinkListingImport,
            ShopifyListingImportService shopifyListingImport,
            DescriptionBackfillService descriptionBackfill,
            PriceBackfillService priceBackfill,
            ShopifySkuBackfillService shopifySkuBackfill
    ) {
        this.marketScanService = marketScanService;
        this.orderSyncService = orderSyncService;
        this.ebayListingImport = ebayListingImport;
        this.brickLinkListingImport = brickLinkListingImport;
        this.shopifyListingImport = shopifyListingImport;
        this.descriptionBackfill = descriptionBackfill;
        this.priceBackfill = priceBackfill;
        this.shopifySkuBackfill = shopifySkuBackfill;
    }

    @PostMapping("/market-scan")
    public Map<String, String> marketScan() {
        marketScanService.scanDueWatches();
        return Map.of("status", "ok");
    }

    @PostMapping("/scan-log-purge")
    public Map<String, Object> scanLogPurge() {
        return Map.of(
                "status", "ok",
                "deleted", marketScanService.purgeOldScanLogs(),
                "deletedSnapshots", marketScanService.purgeOldSnapshots()
        );
    }

    @PostMapping("/sales-sync")
    public Map<String, Object> salesSync() {
        return orderSyncService.sync();
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

    @PostMapping("/shopify-listing-import")
    public ShopifyListingImportService.Report shopifyListingImport(
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return shopifyListingImport.run(dryRun);
    }

    @PostMapping("/description-backfill")
    public DescriptionBackfillService.Report descriptionBackfill(
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return descriptionBackfill.run(dryRun);
    }

    @PostMapping("/price-backfill")
    public PriceBackfillService.Report priceBackfill(
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return priceBackfill.run(dryRun);
    }

    @PostMapping("/shopify-sku-backfill")
    public ShopifySkuBackfillService.Report shopifySkuBackfill(
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return shopifySkuBackfill.run(dryRun);
    }
}
