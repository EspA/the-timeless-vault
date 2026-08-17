package com.thetimelessvault.watch;

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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/set-watches")
public class SetWatchController {

    private final SetWatchService setWatchService;

    public SetWatchController(SetWatchService setWatchService) {
        this.setWatchService = setWatchService;
    }

    public record CreateRequest(
            String setNumber,
            Boolean enabled,
            String ebaySearchQuery,
            Integer ebayFeedbackMin,
            String ebayExcludeWords,
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
            boolean enabled,
            String ebaySearchQuery,
            int ebayFeedbackMin,
            String ebayExcludeWords,
            int ebayScanIntervalMinutes,
            int bricklinkScanIntervalMinutes,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Instant lastScannedAt,
            Instant updatedAt
    ) {
        static SetWatchView from(SetWatch watch) {
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
                    watch.isEnabled(),
                    watch.getEbaySearchQuery(),
                    watch.getEbayFeedbackMin(),
                    watch.getEbayExcludeWords(),
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
        return setWatchService.list().stream().map(SetWatchView::from).toList();
    }

    @GetMapping("/{id}")
    public SetWatchView get(@PathVariable UUID id) {
        return SetWatchView.from(setWatchService.get(id));
    }

    @PostMapping
    public SetWatchView create(@RequestBody CreateRequest request) {
        return SetWatchView.from(setWatchService.create(
                request.setNumber(),
                request.enabled(),
                request.ebaySearchQuery(),
                request.ebayFeedbackMin(),
                request.ebayExcludeWords(),
                request.ebayScanIntervalMinutes(),
                request.bricklinkScanIntervalMinutes(),
                request.minPrice(),
                request.maxPrice()
        ));
    }

    @PutMapping("/{id}")
    public SetWatchView update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        return SetWatchView.from(setWatchService.update(
                id,
                request.enabled(),
                request.ebaySearchQuery(),
                request.ebayFeedbackMin(),
                request.ebayExcludeWords(),
                request.ebayScanIntervalMinutes(),
                request.bricklinkScanIntervalMinutes(),
                request.minPrice(),
                request.maxPrice()
        ));
    }

    @PostMapping("/{id}/refresh")
    public SetWatchView refresh(@PathVariable UUID id) {
        return SetWatchView.from(setWatchService.refresh(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        setWatchService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
