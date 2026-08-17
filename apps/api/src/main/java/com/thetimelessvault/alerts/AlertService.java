package com.thetimelessvault.alerts;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.market.MarketListing;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.settings.WatchDefaults;
import com.thetimelessvault.watch.SetWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertEventRepository alerts;
    private final WatchRuleRepository watchRules;
    private final PriceGuardRepository priceGuards;
    private final JavaMailSender mailSender;
    private final AppProperties properties;
    private final WatchDefaults watchDefaults;

    public AlertService(
            AlertEventRepository alerts,
            WatchRuleRepository watchRules,
            PriceGuardRepository priceGuards,
            JavaMailSender mailSender,
            AppProperties properties,
            WatchDefaults watchDefaults
    ) {
        this.alerts = alerts;
        this.watchRules = watchRules;
        this.priceGuards = priceGuards;
        this.mailSender = mailSender;
        this.properties = properties;
        this.watchDefaults = watchDefaults;
    }

    public List<AlertEvent> list() {
        return alerts.findAllByOrderByCreatedAtDesc();
    }

    public long unreadCount() {
        return alerts.countByReadAtIsNull();
    }

    @Transactional
    public AlertEvent markRead(UUID id) {
        AlertEvent event = alerts.findById(id).orElseThrow(() -> ApiException.notFound("Alert not found"));
        event.setReadAt(Instant.now());
        return alerts.save(event);
    }

    @Transactional
    public int markAllRead() {
        Instant now = Instant.now();
        List<AlertEvent> unread = alerts.findByReadAtIsNull();
        unread.forEach(event -> event.setReadAt(now));
        alerts.saveAll(unread);
        return unread.size();
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

    public boolean matches(SetWatch watch, MarketListing listing) {
        return watch != null && watch.isEnabled();
    }

    @Transactional
    public void recordNewListing(CatalogItem catalog, Platform platform, MarketListing listing, SetWatch watch) {
        if (!matches(watch, listing)) {
            return;
        }
        String dedupe = "NEW:" + platform + ":" + catalog.getId() + ":" + listing.getFingerprint();
        if (alerts.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        AlertEvent event = AlertEvent.create("NEW_LISTING",
                "New " + platform + " listing for " + catalog.getSetNumber() + " " + catalog.getName(),
                dedupe);
        event.setCatalogItem(catalog);
        event.setPlatform(platform);
        event.setUrl(listing.getUrl());
        event.setBody("Price " + listing.getPrice() + " · " + (listing.getTitle() == null ? "" : listing.getTitle()));
        alerts.save(event);
        email(event);
    }

    @Transactional
    public void recordPriceGuard(ChannelListing listing, String type, BigDecimal yours, BigDecimal market, PriceGuard guard) {
        String dedupe = type + ":" + listing.getId() + ":" + market.stripTrailingZeros().toPlainString();
        if (alerts.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        var catalog = listing.getInventoryItem().getCatalogItem();
        AlertEvent event = AlertEvent.create(type,
                listing.getPlatform() + " price for " + catalog.getSetNumber() + " is " + (type.equals("PRICE_HIGH") ? "above" : "below") + " market",
                dedupe);
        event.setCatalogItem(catalog);
        event.setChannelListing(listing);
        event.setPlatform(listing.getPlatform());
        event.setUrl(listing.getLiveUrl());
        event.setBody("Your price " + yours + " vs market average " + market
                + " (thresholds +" + guard.getHighPercent() + "% / -" + guard.getLowPercent() + "%). Adjust manually.");
        alerts.save(event);
        email(event);
    }

    private void email(AlertEvent event) {
        String to = properties.getAlertToEmail();
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getMailFrom());
            message.setTo(to);
            message.setSubject("[The Timeless Vault] " + event.getTitle());
            message.setText(event.getBody() + (event.getUrl() == null ? "" : "\n\n" + event.getUrl()));
            mailSender.send(message);
            event.setEmailedAt(Instant.now());
            alerts.save(event);
        } catch (Exception e) {
            log.warn("Could not send alert email", e);
        }
    }
}
