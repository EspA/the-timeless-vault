package com.thetimelessvault.watch;

import com.thetimelessvault.market.MarketSnapshotRepository;
import com.thetimelessvault.market.MarketStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/set-watches")
public class SetWatchController {

    private final SetWatchService setWatchService;
    private final MarketSnapshotRepository snapshots;

    public SetWatchController(SetWatchService setWatchService, MarketSnapshotRepository snapshots) {
        this.setWatchService = setWatchService;
        this.snapshots = snapshots;
    }

    public record CreateRequest(
            String setNumber,
            Boolean enabled,
            String ebaySearchQuery,
            Integer ebayFeedbackMin,
            String ebayExcludeWords,
            String ebayItemLocation,
            Integer ebayScanIntervalMinutes,
            Integer bricklinkScanIntervalMinutes,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
    }

    public record UpdateRequest(
            Boolean enabled,
            String ebaySearchQuery,
            Integer ebayFeedbackMin,
            String ebayExcludeWords,
            String ebayItemLocation,
            Integer ebayScanIntervalMinutes,
            Integer bricklinkScanIntervalMinutes,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
    }

    public record SetWatchView(
            UUID id,
            UUID catalogId,
            String setNumber,
            String name,
            String theme,
            String subtheme,
            LocalDate releasedDate,
            LocalDate retiredDate,
            Boolean retired,
            Integer piecesCount,
            Integer minifigsCount,
            BigDecimal retailPriceUs,
            BigDecimal currentValueNew,
            Instant brickeconomyFetchedAt,
            BigDecimal ebayCurrentValueNew,
            Instant ebayScannedAt,
            BigDecimal bricklinkCurrentValueNew,
            Instant bricklinkScannedAt,
            BigDecimal medianPrice,
            boolean enabled,
            String ebaySearchQuery,
            int ebayFeedbackMin,
            String ebayExcludeWords,
            String ebayItemLocation,
            int ebayScanIntervalMinutes,
            int bricklinkScanIntervalMinutes,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Instant lastScannedAt,
            Instant updatedAt
    ) {
        static SetWatchView from(SetWatch watch) {
            return from(watch, null);
        }

        static SetWatchView from(SetWatch watch, BigDecimal medianPrice) {
            return new SetWatchView(
                    watch.getId(),
                    watch.getCatalogItem().getId(),
                    watch.getSetNumber(),
                    watch.getName(),
                    watch.getTheme(),
                    watch.getSubtheme(),
                    watch.getReleasedDate(),
                    watch.getRetiredDate(),
                    watch.getRetired(),
                    watch.getPiecesCount(),
                    watch.getMinifigsCount(),
                    watch.getRetailPriceUs(),
                    watch.getCurrentValueNew(),
                    watch.getBrickeconomyFetchedAt(),
                    watch.getEbayCurrentValueNew(),
                    watch.getEbayScannedAt(),
                    watch.getBricklinkCurrentValueNew(),
                    watch.getBricklinkScannedAt(),
                    medianPrice,
                    watch.isEnabled(),
                    watch.getEbaySearchQuery(),
                    watch.getEbayFeedbackMin(),
                    watch.getEbayExcludeWords(),
                    watch.getEbayItemLocation(),
                    watch.getEbayScanIntervalMinutes(),
                    watch.getBricklinkScanIntervalMinutes(),
                    watch.getMinPrice(),
                    watch.getMaxPrice(),
                    watch.getLastScannedAt(),
                    watch.getUpdatedAt()
            );
        }
    }

    @GetMapping
    public List<SetWatchView> list() {
        List<SetWatch> watches = setWatchService.list();
        Map<UUID, BigDecimal> medians = mediansFor(watches.stream()
                .map(watch -> watch.getCatalogItem().getId())
                .toList());
        return watches.stream()
                .map(watch -> SetWatchView.from(watch, medians.get(watch.getCatalogItem().getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public SetWatchView get(@PathVariable UUID id) {
        return toView(setWatchService.get(id));
    }

    @PostMapping
    public SetWatchView create(@RequestBody CreateRequest request) {
        return toView(setWatchService.create(
                request.setNumber(),
                request.enabled(),
                request.ebaySearchQuery(),
                request.ebayFeedbackMin(),
                request.ebayExcludeWords(),
                request.ebayItemLocation(),
                request.ebayScanIntervalMinutes(),
                request.bricklinkScanIntervalMinutes(),
                request.minPrice(),
                request.maxPrice()
        ));
    }

    @PutMapping("/{id}")
    public SetWatchView update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        return toView(setWatchService.update(
                id,
                request.enabled(),
                request.ebaySearchQuery(),
                request.ebayFeedbackMin(),
                request.ebayExcludeWords(),
                request.ebayItemLocation(),
                request.ebayScanIntervalMinutes(),
                request.bricklinkScanIntervalMinutes(),
                request.minPrice(),
                request.maxPrice()
        ));
    }

    @PostMapping("/{id}/refresh")
    public SetWatchView refresh(@PathVariable UUID id) {
        return toView(setWatchService.refresh(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        setWatchService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private SetWatchView toView(SetWatch watch) {
        return SetWatchView.from(watch, mediansFor(List.of(watch.getCatalogItem().getId()))
                .get(watch.getCatalogItem().getId()));
    }

    private Map<UUID, BigDecimal> mediansFor(Collection<UUID> catalogIds) {
        if (catalogIds == null || catalogIds.isEmpty()) {
            return Map.of();
        }
        return MarketStats.combinedMedians(snapshots.findLatestByCatalogItemIdIn(catalogIds));
    }
}
