package com.thetimelessvault.market;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketScanService marketScanService;

    public MarketController(MarketScanService marketScanService) {
        this.marketScanService = marketScanService;
    }

    public record ListingView(
            UUID id,
            Platform platform,
            String title,
            BigDecimal price,
            Integer quantity,
            String condition,
            String seller,
            String sellerCountry,
            String url,
            String imageUrl,
            Integer sellerFeedbackScore,
            String sellerFeedbackPercentage,
            boolean auction,
            BigDecimal currentBid,
            boolean bestOffer,
            BigDecimal shippingCost,
            boolean shippingCalculated,
            boolean own
    ) {
        static ListingView from(MarketListing listing) {
            return new ListingView(
                    listing.getId(),
                    listing.getPlatform(),
                    listing.getTitle(),
                    listing.getPrice(),
                    listing.getQuantity(),
                    listing.getCondition(),
                    listing.getSeller(),
                    listing.getSellerCountry(),
                    listing.getUrl(),
                    listing.getImageUrl(),
                    listing.getSellerFeedbackScore(),
                    listing.getSellerFeedbackPercentage(),
                    listing.isAuction(),
                    listing.getCurrentBid(),
                    listing.isBestOffer(),
                    listing.getShippingCost(),
                    listing.isShippingCalculated(),
                    listing.isOwn()
            );
        }
    }

    public record SnapshotView(Platform platform, String condition, BigDecimal min, BigDecimal avg, BigDecimal median, BigDecimal max, Integer count, Instant scannedAt) {
        static SnapshotView from(MarketSnapshot snapshot) {
            if (snapshot == null) {
                return null;
            }
            return new SnapshotView(snapshot.getPlatform(), snapshot.getCondition(), snapshot.getMinPrice(),
                    snapshot.getAvgPrice(), snapshot.getMedianPrice(), snapshot.getMaxPrice(), snapshot.getListingCount(), snapshot.getScannedAt());
        }
    }

    public record DashboardView(
            UUID catalogId,
            String setNumber,
            String name,
            SnapshotView ebay,
            SnapshotView bricklink,
            List<ListingView> ebayListings,
            List<ListingView> bricklinkListings,
            String ebayError,
            String bricklinkError
    ) {
    }

    @GetMapping("/{catalogId}")
    public DashboardView get(@PathVariable UUID catalogId) {
        return toView(marketScanService.dashboard(catalogId));
    }

    @PostMapping("/{catalogId}/scan")
    public DashboardView scan(
            @PathVariable UUID catalogId,
            @RequestParam(required = false) String platform
    ) {
        Platform parsed = parsePlatform(platform);
        if (parsed == null) {
            return toView(marketScanService.scanAll(catalogId));
        }
        return toView(marketScanService.scan(catalogId, parsed));
    }

    private static Platform parsePlatform(String platform) {
        if (platform == null || platform.isBlank() || "all".equalsIgnoreCase(platform.trim())) {
            return null;
        }
        try {
            Platform value = Platform.valueOf(platform.trim().toUpperCase());
            if (value == Platform.SHOPIFY) {
                throw ApiException.badRequest("Shopify is not a market scan platform.");
            }
            return value;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown market scan platform.");
        }
    }

    private static DashboardView toView(MarketScanService.MarketDashboard dashboard) {
        return new DashboardView(
                dashboard.catalogId(),
                dashboard.setNumber(),
                dashboard.name(),
                SnapshotView.from(dashboard.ebaySnapshot()),
                SnapshotView.from(dashboard.bricklinkSnapshot()),
                dashboard.ebayListings().stream()
                        .sorted(Comparator.comparing(MarketListing::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(ListingView::from)
                        .toList(),
                dashboard.bricklinkListings().stream()
                        .sorted(Comparator.comparing(MarketListing::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(ListingView::from)
                        .toList(),
                dashboard.ebayError(),
                dashboard.bricklinkError()
        );
    }
}
