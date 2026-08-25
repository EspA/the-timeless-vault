package com.thetimelessvault.market;

import com.thetimelessvault.common.Platform;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scan-logs")
public class ScanLogsController {

    private final MarketScanService marketScanService;

    public ScanLogsController(MarketScanService marketScanService) {
        this.marketScanService = marketScanService;
    }

    public record ScanLogView(
            UUID id,
            UUID catalogId,
            String setNumber,
            String setName,
            Platform platform,
            ScanTrigger trigger,
            ScanStatus status,
            Integer listingCount,
            String message,
            Instant scannedAt
    ) {
        static ScanLogView from(ScanLog log) {
            return new ScanLogView(
                    log.getId(),
                    log.getCatalogItemId(),
                    log.getSetNumber(),
                    log.getSetName(),
                    log.getPlatform(),
                    log.getScanTrigger(),
                    log.getStatus(),
                    log.getListingCount(),
                    log.getMessage(),
                    log.getScannedAt()
            );
        }
    }

    public record ScanLogsPageView(
            List<ScanLogView> items,
            int page,
            int size,
            long total,
            int totalPages
    ) {
    }

    @GetMapping
    public ScanLogsPageView list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var result = marketScanService.scanLogs(page, size);
        return new ScanLogsPageView(
                result.getContent().stream().map(ScanLogView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
