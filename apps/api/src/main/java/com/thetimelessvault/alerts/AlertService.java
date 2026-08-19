package com.thetimelessvault.alerts;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.settings.WatchDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final WatchRuleRepository watchRules;
    private final PriceGuardRepository priceGuards;
    private final WatchDefaults watchDefaults;

    public AlertService(
            WatchRuleRepository watchRules,
            PriceGuardRepository priceGuards,
            WatchDefaults watchDefaults
    ) {
        this.watchRules = watchRules;
        this.priceGuards = priceGuards;
        this.watchDefaults = watchDefaults;
    }

    public List<WatchRule> watchRules() {
        return watchRules.findAllWithCatalog();
    }

    public WatchRule watchRule(UUID catalogId) {
        return watchRules.findByCatalogItemId(catalogId).orElse(null);
    }

    @Transactional
    public WatchRule upsertWatch(
            CatalogItem catalog,
            Boolean enabled,
            String ebaySearchQuery,
            Integer ebayFeedbackMin,
            String ebayExcludeWords
    ) {
        WatchRule rule = watchRules.findByCatalogItemId(catalog.getId()).orElseGet(() -> WatchRule.create(catalog));
        if (enabled != null) {
            rule.setEnabled(enabled);
        }
        rule.setConditionFilter("NEW");
        if (ebaySearchQuery != null && !ebaySearchQuery.isBlank()) {
            rule.setEbaySearchQuery(ebaySearchQuery.trim());
        } else if (rule.getEbaySearchQuery() == null || rule.getEbaySearchQuery().isBlank()) {
            rule.setEbaySearchQuery(com.thetimelessvault.common.ThemeMapper.suggestedEbaySearch(
                    catalog.getTheme(), catalog.getSetNumber(), catalog.getName()));
        }
        if (ebayFeedbackMin != null) {
            rule.setEbayFeedbackMin(ebayFeedbackMin);
        }
        if (ebayExcludeWords != null && !ebayExcludeWords.isBlank()) {
            rule.setEbayExcludeWords(ebayExcludeWords.trim());
        } else if (rule.getEbayExcludeWords() == null || rule.getEbayExcludeWords().isBlank()) {
            rule.setEbayExcludeWords(watchDefaults.excludeWords());
        }
        rule.touch();
        return watchRules.save(rule);
    }

    public List<PriceGuard> priceGuards() {
        return priceGuards.findAllWithListing();
    }

    @Transactional
    public PriceGuard upsertGuard(ChannelListing listing, Boolean enabled, BigDecimal high, BigDecimal low) {
        PriceGuard guard = priceGuards.findByChannelListingId(listing.getId()).orElseGet(() -> PriceGuard.create(listing));
        if (enabled != null) {
            guard.setEnabled(enabled);
        }
        if (high != null) {
            guard.setHighPercent(high);
        }
        if (low != null) {
            guard.setLowPercent(low);
        }
        guard.touch();
        return priceGuards.save(guard);
    }
}
