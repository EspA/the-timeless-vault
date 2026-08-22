package com.thetimelessvault.alerts;

import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.publish.ChannelListingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class WatchController {

    private final WatchService watchService;
    private final CatalogService catalogService;
    private final ChannelListingRepository listings;

    public WatchController(WatchService watchService, CatalogService catalogService, ChannelListingRepository listings) {
        this.watchService = watchService;
        this.catalogService = catalogService;
        this.listings = listings;
    }

    public record WatchRequest(boolean enabled, String ebaySearchQuery, Integer ebayFeedbackMin, String ebayExcludeWords) {
    }

    public record WatchView(
            UUID id,
            UUID catalogId,
            String setNumber,
            String name,
            boolean enabled,
            String ebaySearchQuery,
            int ebayFeedbackMin,
            String ebayExcludeWords
    ) {
        static WatchView from(WatchRule rule) {
            return from(rule.getCatalogItem(), rule);
        }

        static WatchView from(com.thetimelessvault.catalog.CatalogItem catalog, WatchRule rule) {
            String query = rule == null ? null : rule.getEbaySearchQuery();
            if (query == null || query.isBlank()) {
                query = com.thetimelessvault.common.ThemeMapper.suggestedEbaySearch(
                        catalog.getTheme(), catalog.getSetNumber(), catalog.getName());
            }
            return new WatchView(
                    rule == null ? null : rule.getId(),
                    catalog.getId(),
                    catalog.getSetNumber(),
                    catalog.getName(),
                    rule != null && rule.isEnabled(),
                    query,
                    rule == null ? 1 : rule.getEbayFeedbackMin(),
                    rule == null ? "" : rule.getEbayExcludeWords()
            );
        }
    }

    public record GuardRequest(boolean enabled, BigDecimal highPercent, BigDecimal lowPercent) {
    }

    public record GuardView(UUID id, UUID listingId, Platform platform, boolean enabled, BigDecimal highPercent, BigDecimal lowPercent) {
        static GuardView from(PriceGuard guard) {
            return new GuardView(guard.getId(), guard.getChannelListing().getId(), guard.getChannelListing().getPlatform(),
                    guard.isEnabled(), guard.getHighPercent(), guard.getLowPercent());
        }
    }

    @GetMapping("/watch-rules")
    public List<WatchView> watchRules() {
        return watchService.watchRules().stream().map(WatchView::from).toList();
    }

    @GetMapping("/watch-rules/{catalogId}")
    public WatchView watchRule(@PathVariable UUID catalogId) {
        var catalog = catalogService.get(catalogId);
        return WatchView.from(catalog, watchService.watchRule(catalogId));
    }

    @PutMapping("/watch-rules/{catalogId}")
    public WatchView upsertWatch(@PathVariable UUID catalogId, @RequestBody WatchRequest request) {
        var catalog = catalogService.get(catalogId);
        return WatchView.from(catalog, watchService.upsertWatch(
                catalog,
                request.enabled(),
                request.ebaySearchQuery(),
                request.ebayFeedbackMin(),
                request.ebayExcludeWords()
        ));
    }

    @GetMapping("/price-guards")
    public List<GuardView> guards() {
        return watchService.priceGuards().stream().map(GuardView::from).toList();
    }

    @PutMapping("/price-guards/{listingId}")
    public GuardView upsertGuard(@PathVariable UUID listingId, @RequestBody GuardRequest request) {
        return GuardView.from(watchService.upsertGuard(
                listings.findById(listingId).orElseThrow(),
                request.enabled(),
                request.highPercent(),
                request.lowPercent()
        ));
    }
}
