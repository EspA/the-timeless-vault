package com.thetimelessvault.watch;

import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.ebay.EbayMarketFilters;
import com.thetimelessvault.market.ScanLogRepository;
import com.thetimelessvault.settings.WatchDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SetWatchService {

    private final SetWatchRepository watches;
    private final CatalogService catalogService;
    private final WatchDefaults watchDefaults;
    private final BuyingOpportunityService opportunities;
    private final ScanLogRepository scanLogs;

    public SetWatchService(
            SetWatchRepository watches,
            CatalogService catalogService,
            WatchDefaults watchDefaults,
            BuyingOpportunityService opportunities,
            ScanLogRepository scanLogs
    ) {
        this.watches = watches;
        this.catalogService = catalogService;
        this.watchDefaults = watchDefaults;
        this.opportunities = opportunities;
        this.scanLogs = scanLogs;
    }

    public List<SetWatch> list() {
        return watches.findAllWithCatalog();
    }

    public SetWatch get(UUID id) {
        return watches.findWithCatalogById(id).orElseThrow(() -> ApiException.notFound("Set watch not found"));
    }

    public SetWatch findByCatalogId(UUID catalogId) {
        return watches.findByCatalogItemId(catalogId).orElse(null);
    }

    public List<SetWatch> enabled() {
        return watches.findEnabledWithCatalog();
    }

    @Transactional
    public SetWatch create(
            String setNumber,
            Boolean enabled,
            String ebaySearchQuery,
            Integer ebayFeedbackMin,
            String ebayExcludeWords,
            String ebayItemLocation,
            String ebayListingType,
            Integer ebayScanIntervalMinutes,
            Integer bricklinkScanIntervalMinutes,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        CatalogItem catalog = catalogService.lookup(setNumber, false);
        if (watches.findBySetNumberIgnoreCase(catalog.getSetNumber()).isPresent()) {
            throw ApiException.conflict("This set is already on the watch list.");
        }
        SetWatch watch = SetWatch.create(catalog);
        applyFilters(watch, catalog, enabled == null || enabled, ebaySearchQuery, ebayFeedbackMin, ebayExcludeWords, ebayItemLocation, ebayListingType, ebayScanIntervalMinutes, bricklinkScanIntervalMinutes, minPrice, maxPrice, true);
        return get(watches.saveAndFlush(watch).getId());
    }

    @Transactional
    public boolean ensureWatch(CatalogItem catalog, boolean enabled) {
        if (catalog == null) {
            return false;
        }
        var existing = watches.findByCatalogItemId(catalog.getId());
        if (existing.isEmpty() && catalog.getSetNumber() != null && !catalog.getSetNumber().isBlank()) {
            existing = watches.findBySetNumberIgnoreCase(catalog.getSetNumber());
        }
        if (existing.isPresent()) {
            return false;
        }
        SetWatch watch = SetWatch.create(catalog);
        applyFilters(watch, catalog, enabled, null, null, null, null, null, null, null, null, null, true);
        watches.saveAndFlush(watch);
        return true;
    }

    @Transactional
    public SetWatch update(
            UUID id,
            Boolean enabled,
            String ebaySearchQuery,
            Integer ebayFeedbackMin,
            String ebayExcludeWords,
            String ebayItemLocation,
            String ebayListingType,
            Integer ebayScanIntervalMinutes,
            Integer bricklinkScanIntervalMinutes,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        SetWatch watch = get(id);
        applyFilters(watch, watch.getCatalogItem(), enabled, ebaySearchQuery, ebayFeedbackMin, ebayExcludeWords, ebayItemLocation, ebayListingType, ebayScanIntervalMinutes, bricklinkScanIntervalMinutes, minPrice, maxPrice, false);
        watch.touch();
        return get(watches.saveAndFlush(watch).getId());
    }

    @Transactional
    public SetWatch refresh(UUID id) {
        SetWatch watch = get(id);
        CatalogItem catalog = catalogService.refresh(watch.getSetNumber());
        watch.copyCatalog(catalog);
        watch.touch();
        return get(watches.saveAndFlush(watch).getId());
    }

    @Transactional
    public void delete(UUID id) {
        SetWatch watch = get(id);
        CatalogItem catalog = watch.getCatalogItem();
        opportunities.deleteNewListings(catalog.getId());
        scanLogs.deleteByCatalogItemId(catalog.getId());
        if (catalog.getSetNumber() != null && !catalog.getSetNumber().isBlank()) {
            scanLogs.deleteBySetNumberIgnoreCase(catalog.getSetNumber());
        }
        watches.delete(watch);
    }

    private void applyFilters(
            SetWatch watch,
            CatalogItem catalog,
            Boolean enabled,
            String ebaySearchQuery,
            Integer ebayFeedbackMin,
            String ebayExcludeWords,
            String ebayItemLocation,
            String ebayListingType,
            Integer ebayScanIntervalMinutes,
            Integer bricklinkScanIntervalMinutes,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean creating
    ) {
        if (enabled != null) {
            watch.setEnabled(enabled);
        }
        if (ebaySearchQuery != null && !ebaySearchQuery.isBlank()) {
            watch.setEbaySearchQuery(ebaySearchQuery.trim());
        } else if (watch.getEbaySearchQuery() == null || watch.getEbaySearchQuery().isBlank()) {
            watch.setEbaySearchQuery(ThemeMapper.suggestedEbaySearch(
                    catalog.getTheme(), catalog.getSetNumber(), catalog.getName()));
        }
        if (ebayFeedbackMin != null) {
            watch.setEbayFeedbackMin(ebayFeedbackMin);
        }
        if (ebayItemLocation != null && !ebayItemLocation.isBlank()) {
            watch.setEbayItemLocation(ebayItemLocation);
        } else if (creating && (watch.getEbayItemLocation() == null || watch.getEbayItemLocation().isBlank())) {
            watch.setEbayItemLocation(EbayMarketFilters.LOCATION_NORTH_AMERICA);
        }
        if (ebayListingType != null && !ebayListingType.isBlank()) {
            watch.setEbayListingType(ebayListingType);
        } else if (creating && (watch.getEbayListingType() == null || watch.getEbayListingType().isBlank())) {
            watch.setEbayListingType(EbayMarketFilters.LISTING_TYPE_ALL);
        }
        watch.setEbayExcludeWords(resolveExcludeWords(
                ebayExcludeWords,
                watch.getEbayExcludeWords(),
                creating,
                watchDefaults.excludeWords()
        ));
        if (ebayScanIntervalMinutes != null) {
            watch.setEbayScanIntervalMinutes(ebayScanIntervalMinutes);
        }
        if (bricklinkScanIntervalMinutes != null) {
            watch.setBricklinkScanIntervalMinutes(bricklinkScanIntervalMinutes);
        }
        watch.setPriceRange(minPrice, maxPrice);
    }

    static String resolveExcludeWords(String incoming, String existing, boolean creating, String defaults) {
        if (incoming != null) {
            return incoming.trim();
        }
        if (existing != null) {
            return existing;
        }
        return creating ? defaults : "";
    }
}
