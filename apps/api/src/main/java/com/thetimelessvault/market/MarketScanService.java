package com.thetimelessvault.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.thetimelessvault.alerts.AlertService;
import com.thetimelessvault.alerts.PriceGuard;
import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.bricklink.BrickLinkForSale;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogItemRepository;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ListingStatus;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.ebay.EbayListingDetails;
import com.thetimelessvault.ebay.EbayMarketFilters;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.settings.WatchDefaults;
import com.thetimelessvault.watch.SetWatch;
import com.thetimelessvault.watch.SetWatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketScanService {

    private static final Logger log = LoggerFactory.getLogger(MarketScanService.class);

    private final SetWatchRepository setWatches;
    private final CatalogItemRepository catalogItems;
    private final MarketSnapshotRepository snapshots;
    private final MarketListingRepository marketListings;
    private final ChannelListingRepository channelListings;
    private final PriceGuardRepository priceGuards;
    private final EbayClient ebayClient;
    private final BrickLinkClient brickLinkClient;
    private final AlertService alerts;
    private final MarketScanLogRepository scanLogs;
    private final WatchDefaults watchDefaults;

    public MarketScanService(
            SetWatchRepository setWatches,
            CatalogItemRepository catalogItems,
            MarketSnapshotRepository snapshots,
            MarketListingRepository marketListings,
            ChannelListingRepository channelListings,
            PriceGuardRepository priceGuards,
            EbayClient ebayClient,
            BrickLinkClient brickLinkClient,
            AlertService alerts,
            MarketScanLogRepository scanLogs,
            WatchDefaults watchDefaults
    ) {
        this.setWatches = setWatches;
        this.catalogItems = catalogItems;
        this.snapshots = snapshots;
        this.marketListings = marketListings;
        this.channelListings = channelListings;
        this.priceGuards = priceGuards;
        this.ebayClient = ebayClient;
        this.brickLinkClient = brickLinkClient;
        this.alerts = alerts;
        this.scanLogs = scanLogs;
        this.watchDefaults = watchDefaults;
    }

    public void scanDueWatches() {
        Instant now = Instant.now();
        for (SetWatch watch : setWatches.findEnabledWithCatalog()) {
            UUID catalogId = watch.getCatalogItem().getId();
            if (watch.isEbayDue(now)) {
                try {
                    scan(catalogId, Platform.EBAY, ScanTrigger.AUTOMATIC);
                } catch (Exception e) {
                    log.warn("eBay market scan failed for {}", watch.getSetNumber(), e);
                }
            }
            if (watch.isBrickLinkDue(now)) {
                try {
                    scan(catalogId, Platform.BRICKLINK, ScanTrigger.AUTOMATIC);
                } catch (Exception e) {
                    log.warn("BrickLink market scan failed for {}", watch.getSetNumber(), e);
                }
            }
        }
        evaluatePriceGuards();
    }

    @Transactional
    public MarketDashboard scan(UUID catalogId, Platform platform) {
        return scan(catalogId, platform, ScanTrigger.MANUAL);
    }

    @Transactional
    public MarketDashboard scan(UUID catalogId, Platform platform, ScanTrigger trigger) {
        if (platform != Platform.EBAY && platform != Platform.BRICKLINK) {
            throw ApiException.badRequest("Scan eBay or BrickLink separately.");
        }
        CatalogItem catalog = catalogItems.findById(catalogId).orElseThrow();
        SetWatch watch = setWatches.findByCatalogItemId(catalogId).orElse(null);
        String ebayError = null;
        PlatformScanResult result;
        if (platform == Platform.EBAY) {
            result = scanEbay(catalog, watch);
            ebayError = result.message();
        } else {
            result = scanBrickLink(catalog, watch);
        }
        scanLogs.save(MarketScanLog.create(
                catalog,
                platform,
                trigger,
                result.failed() ? ScanStatus.FAILED : ScanStatus.SUCCESS,
                result.listingCount(),
                result.message()
        ));
        return dashboard(catalogId, ebayError);
    }

    public Page<MarketScanLog> scanLog(int page, int size) {
        int pageSize = Math.min(20, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        return scanLogs.findAllByOrderByScannedAtDesc(PageRequest.of(pageIndex, pageSize));
    }

    public MarketDashboard dashboard(UUID catalogId) {
        return dashboard(catalogId, null);
    }

    private MarketDashboard dashboard(UUID catalogId, String ebayError) {
        CatalogItem catalog = catalogItems.findById(catalogId).orElseThrow();
        var ebaySnap = snapshots.findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(catalogId, Platform.EBAY, "NEW");
        if (ebaySnap.isEmpty()) {
            ebaySnap = snapshots.findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(catalogId, Platform.EBAY, "ANY");
        }
        var blNew = snapshots.findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(catalogId, Platform.BRICKLINK, "N");
        List<MarketListing> ebay = ebaySnap.map(s -> marketListings.findBySnapshotId(s.getId())).orElse(List.of());
        List<MarketListing> bricklink = blNew.map(s -> marketListings.findBySnapshotId(s.getId())).orElse(List.of());
        if (ebayError == null && ebay.isEmpty() && !ebayClient.browseConfigured()) {
            ebayError = "eBay is not configured.";
        }
        return new MarketDashboard(catalog.getId(), catalog.getSetNumber(), catalog.getName(),
                ebaySnap.orElse(null), blNew.orElse(null), ebay, bricklink, ebayError);
    }

    private PlatformScanResult scanEbay(CatalogItem catalog, SetWatch watch) {
        if (!ebayClient.browseConfigured()) {
            return PlatformScanResult.fail("eBay is not configured.");
        }
        Set<String> previous = previousFingerprints(catalog.getId(), Platform.EBAY);
        String query = ebayQuery(catalog, watch);
        int feedbackMin = watch == null ? 1 : watch.getEbayFeedbackMin();
        String excludeWords = watch != null && watch.getEbayExcludeWords() != null
                ? watch.getEbayExcludeWords()
                : watchDefaults.excludeWords();
        JsonNode root;
        try {
            String setNumber = ThemeMapper.displaySetNumber(catalog.getSetNumber());
            root = ebayClient.searchBrowse(setNumber.isBlank() ? query : "LEGO " + setNumber);
        } catch (Exception e) {
            log.warn("eBay market scan failed for {}", catalog.getSetNumber(), e);
            return PlatformScanResult.fail(e.getMessage() == null ? "eBay search failed." : e.getMessage());
        }
        int returned = root.path("itemSummaries").size();
        MarketSnapshot snapshot = MarketSnapshot.create(catalog, Platform.EBAY, "NEW");
        BigDecimal min = null;
        BigDecimal max = null;
        BigDecimal sum = BigDecimal.ZERO;
        List<BigDecimal> prices = new ArrayList<>();
        int count = 0;
        snapshots.save(snapshot);
        for (JsonNode item : root.path("itemSummaries")) {
            if (!EbayMarketFilters.matchesWatch(item, feedbackMin, query, excludeWords)) {
                continue;
            }
            BigDecimal price = decimal(item.path("price").path("value").asText(null));
            if (watch != null && !watch.acceptsPrice(price)) {
                continue;
            }
            MarketListing listing = MarketListing.create(snapshot, catalog, Platform.EBAY);
            String itemId = item.path("itemId").asText();
            listing.setExternalId(itemId);
            listing.setFingerprint(itemId);
            listing.setTitle(item.path("title").asText(null));
            listing.setPrice(price);
            listing.setCondition(item.path("condition").asText("New"));
            listing.setSeller(item.path("seller").path("username").asText(null));
            listing.setUrl(item.path("itemWebUrl").asText(null));
            EbayListingDetails.from(item).applyTo(listing);
            listing.setOwn(isOwnEbay(itemId));
            marketListings.save(listing);
            if (price != null) {
                min = min == null || price.compareTo(min) < 0 ? price : min;
                max = max == null || price.compareTo(max) > 0 ? price : max;
                sum = sum.add(price);
                prices.add(price);
                count++;
            }
            if (watch != null && !previous.isEmpty() && !previous.contains(itemId)) {
                alerts.recordNewListing(catalog, Platform.EBAY, listing, watch);
            }
        }
        snapshot.setMinPrice(min);
        snapshot.setMaxPrice(max);
        snapshot.setAvgPrice(count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        snapshot.setMedianPrice(MarketStats.median(prices));
        snapshot.setListingCount(count);
        snapshots.save(snapshot);
        if (watch != null) {
            watch.recordEbayScan(snapshot.getAvgPrice(), snapshot.getScannedAt());
            setWatches.save(watch);
        }
        if (count == 0 && returned > 0) {
            return PlatformScanResult.ok(count, "eBay returned " + returned + " listings, but none matched North America, feedback min "
                    + feedbackMin + ", your title search/exclude words, or your price range.");
        }
        if (count == 0) {
            return PlatformScanResult.ok(count, "eBay returned no listings for \"" + query + "\".");
        }
        return PlatformScanResult.ok(count, null);
    }

    private PlatformScanResult scanBrickLink(CatalogItem catalog, SetWatch watch) {
        try {
            return scanBrickLinkLots(catalog, watch);
        } catch (Exception e) {
            log.warn("BrickLink market scan failed for {}", catalog.getSetNumber(), e);
            return PlatformScanResult.fail(e.getMessage() == null ? "BrickLink scan failed." : e.getMessage());
        }
    }

    private PlatformScanResult scanBrickLinkLots(CatalogItem catalog, SetWatch watch) {
        List<BrickLinkForSale.Lot> lots = brickLinkClient.forSaleNewSealedShipsToUsa(catalog.getSetNumber());
        Set<String> previous = previousFingerprints(catalog.getId(), Platform.BRICKLINK);
        if (previous.stream().noneMatch(fingerprint -> fingerprint.chars().allMatch(Character::isDigit))) {
            previous = Set.of();
        }
        MarketSnapshot snapshot = MarketSnapshot.create(catalog, Platform.BRICKLINK, "N");
        BigDecimal min = null;
        BigDecimal max = null;
        BigDecimal sum = BigDecimal.ZERO;
        List<BigDecimal> prices = new ArrayList<>();
        int count = 0;
        snapshots.save(snapshot);
        for (BrickLinkForSale.Lot lot : lots) {
            if (watch != null && !watch.acceptsPrice(lot.price())) {
                continue;
            }
            MarketListing listing = MarketListing.create(snapshot, catalog, Platform.BRICKLINK);
            listing.setExternalId(lot.fingerprint());
            listing.setFingerprint(lot.fingerprint());
            listing.setTitle(lot.title());
            listing.setPrice(lot.price());
            listing.setQuantity(lot.quantity());
            listing.setCondition(lot.condition());
            listing.setSeller(lot.seller());
            listing.setSellerCountry(lot.sellerCountry());
            listing.setImageUrl(lot.imageUrl());
            listing.setUrl(lot.url());
            marketListings.save(listing);
            if (lot.price() != null) {
                min = min == null || lot.price().compareTo(min) < 0 ? lot.price() : min;
                max = max == null || lot.price().compareTo(max) > 0 ? lot.price() : max;
                sum = sum.add(lot.price());
                prices.add(lot.price());
                count++;
            }
            if (watch != null && !previous.isEmpty() && !previous.contains(lot.fingerprint())) {
                alerts.recordNewListing(catalog, Platform.BRICKLINK, listing, watch);
            }
        }
        snapshot.setMinPrice(min);
        snapshot.setMaxPrice(max);
        snapshot.setAvgPrice(count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        snapshot.setMedianPrice(MarketStats.median(prices));
        snapshot.setListingCount(count);
        snapshots.save(snapshot);
        if (watch != null) {
            watch.recordBrickLinkScan(snapshot.getAvgPrice(), snapshot.getScannedAt());
            setWatches.save(watch);
        }
        return PlatformScanResult.ok(count, null);
    }

    private static String ebayQuery(CatalogItem catalog, SetWatch watch) {
        if (watch != null && watch.getEbaySearchQuery() != null && !watch.getEbaySearchQuery().isBlank()) {
            return watch.getEbaySearchQuery().trim();
        }
        return ThemeMapper.suggestedEbaySearch(catalog.getTheme(), catalog.getSetNumber(), catalog.getName());
    }

    private void evaluatePriceGuards() {
        for (PriceGuard guard : priceGuards.findEnabledWithListing()) {
            ChannelListing listing = guard.getChannelListing();
            if (listing.getStatus() != ListingStatus.PUBLISHED || listing.getLastPublishedPrice() == null) {
                continue;
            }
            String condition = listing.getPlatform() == Platform.BRICKLINK ? "N" : "NEW";
            snapshots.findFirstByCatalogItemIdAndPlatformAndConditionOrderByScannedAtDesc(
                    listing.getInventoryItem().getCatalogItem().getId(), listing.getPlatform(), condition
            ).ifPresent(snapshot -> {
                if (snapshot.getAvgPrice() == null) {
                    return;
                }
                BigDecimal yours = listing.getLastPublishedPrice();
                BigDecimal avg = snapshot.getAvgPrice();
                BigDecimal high = avg.multiply(BigDecimal.ONE.add(guard.getHighPercent().movePointLeft(2)));
                BigDecimal low = avg.multiply(BigDecimal.ONE.subtract(guard.getLowPercent().movePointLeft(2)));
                if (yours.compareTo(high) > 0) {
                    alerts.recordPriceGuard(listing, "PRICE_HIGH", yours, avg, guard);
                } else if (yours.compareTo(low) < 0) {
                    alerts.recordPriceGuard(listing, "PRICE_LOW", yours, avg, guard);
                }
            });
        }
    }

    private Set<String> previousFingerprints(UUID catalogId, Platform platform) {
        Set<String> set = new HashSet<>();
        marketListings.findByCatalogItemIdAndPlatform(catalogId, platform).forEach(l -> set.add(l.getFingerprint()));
        return set;
    }

    private boolean isOwnEbay(String itemId) {
        return channelListings.findAllByStatusWithItem(ListingStatus.PUBLISHED).stream()
                .anyMatch(l -> l.getPlatform() == Platform.EBAY && itemId.equals(l.getExternalId()));
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    public record MarketDashboard(
            UUID catalogId,
            String setNumber,
            String name,
            MarketSnapshot ebaySnapshot,
            MarketSnapshot bricklinkSnapshot,
            List<MarketListing> ebayListings,
            List<MarketListing> bricklinkListings,
            String ebayError
    ) {
    }

    private record PlatformScanResult(int listingCount, String message, boolean failed) {
        static PlatformScanResult ok(int listingCount, String message) {
            return new PlatformScanResult(listingCount, message, false);
        }

        static PlatformScanResult fail(String message) {
            return new PlatformScanResult(0, message, true);
        }
    }
}
