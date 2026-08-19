package com.thetimelessvault.publish;

import com.thetimelessvault.common.Platform;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listing-logs")
public class ListingLogsController {

    private final ListingLogService listingLogs;

    public ListingLogsController(ListingLogService listingLogs) {
        this.listingLogs = listingLogs;
    }

    public record ListingLogView(
            UUID id,
            UUID inventoryItemId,
            String sku,
            String setNumber,
            String itemTitle,
            Platform platform,
            ListingAction action,
            ListingLogStatus status,
            String message,
            Instant loggedAt
    ) {
        static ListingLogView from(ListingLog log) {
            return new ListingLogView(
                    log.getId(),
                    log.getInventoryItemId(),
                    log.getSku(),
                    log.getSetNumber(),
                    log.getItemTitle(),
                    log.getPlatform(),
                    log.getAction(),
                    log.getStatus(),
                    log.getMessage(),
                    log.getLoggedAt()
            );
        }
    }

    public record ListingLogsPageView(
            List<ListingLogView> items,
            int page,
            int size,
            long total,
            int totalPages
    ) {
    }

    @GetMapping
    public ListingLogsPageView list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = listingLogs.list(page, size);
        return new ListingLogsPageView(
                result.getContent().stream().map(ListingLogView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
