package com.thetimelessvault.opportunities;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.market.MarketListing;
import com.thetimelessvault.market.ScanTrigger;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.settings.AlertMailer;
import com.thetimelessvault.watch.SetWatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BuyingOpportunityService {

    private final BuyingOpportunityRepository opportunities;
    private final AlertMailer alertMailer;

    public BuyingOpportunityService(BuyingOpportunityRepository opportunities, AlertMailer alertMailer) {
        this.opportunities = opportunities;
        this.alertMailer = alertMailer;
    }

    public List<BuyingOpportunity> list() {
        return opportunities.findAllByOrderByCreatedAtDesc();
    }

    public long unreadCount() {
        return opportunities.countByReadAtIsNull();
    }

    @Transactional
    public BuyingOpportunity markRead(UUID id) {
        BuyingOpportunity opportunity = opportunities.findById(id)
                .orElseThrow(() -> ApiException.notFound("Buying opportunity not found"));
        opportunity.setReadAt(Instant.now());
        return opportunities.save(opportunity);
    }

    @Transactional
    public int markAllRead() {
        Instant now = Instant.now();
        List<BuyingOpportunity> unread = opportunities.findByReadAtIsNull();
        unread.forEach(opportunity -> opportunity.setReadAt(now));
        opportunities.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void deleteNewListings(UUID catalogItemId) {
        if (catalogItemId == null) {
            return;
        }
        opportunities.deleteByCatalogItem_IdAndType(catalogItemId, BuyingOpportunity.TYPE_BUYING_OPPORTUNITY);
    }

    public boolean matches(SetWatch watch, MarketListing listing) {
        return watch != null && watch.isEnabled() && watch.acceptsPrice(listing.getPrice());
    }

    @Transactional
    public void recordNewListing(CatalogItem catalog, Platform platform, MarketListing listing, SetWatch watch) {
        if (!matches(watch, listing)) {
            return;
        }
        String dedupe = "NEW:" + platform + ":" + catalog.getId() + ":" + listing.getFingerprint();
        if (opportunities.findByDedupeKeyAndScanTrigger(dedupe, ScanTrigger.AUTOMATIC).isPresent()) {
            return;
        }
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_BUYING_OPPORTUNITY,
                "New " + platform + " listing for " + catalog.getSetNumber() + " " + catalog.getName(),
                dedupe);
        opportunity.setCatalogItem(catalog);
        opportunity.setPlatform(platform);
        opportunity.setScanTrigger(ScanTrigger.AUTOMATIC);
        opportunity.setUrl(listing.getUrl());
        opportunity.setBody("Price " + listing.getPrice() + " · " + (listing.getTitle() == null ? "" : listing.getTitle()));
        opportunities.save(opportunity);
        email(opportunity);
    }

    @Transactional
    public void recordPriceGuard(
            ChannelListing listing,
            String type,
            BigDecimal yours,
            BigDecimal market,
            BigDecimal highPercent,
            BigDecimal lowPercent
    ) {
        String dedupe = type + ":" + listing.getId() + ":" + market.stripTrailingZeros().toPlainString();
        if (opportunities.findByDedupeKey(dedupe).isPresent()) {
            return;
        }
        var catalog = listing.getInventoryItem().getCatalogItem();
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                type,
                listing.getPlatform() + " price for " + catalog.getSetNumber() + " is "
                        + (BuyingOpportunity.TYPE_PRICE_HIGH.equals(type) ? "above" : "below") + " market",
                dedupe);
        opportunity.setCatalogItem(catalog);
        opportunity.setChannelListing(listing);
        opportunity.setPlatform(listing.getPlatform());
        opportunity.setUrl(listingUrl(listing));
        opportunity.setBody("Your price " + yours + " vs market average " + market
                + " (thresholds +" + highPercent + "% / -" + lowPercent + "%). Adjust manually.");
        opportunities.save(opportunity);
        email(opportunity);
    }

    static String listingUrl(ChannelListing listing) {
        String liveUrl = listing.getLiveUrl();
        if (usableHttpUrl(liveUrl) && !brokenBrickLinkStoreUrl(liveUrl)) {
            return liveUrl;
        }
        String photoUrl = listing.getBricklinkPhotoUploadUrl();
        if (usableHttpUrl(photoUrl)) {
            return photoUrl;
        }
        if (listing.getInventoryItem() != null && listing.getInventoryItem().getId() != null) {
            return "/inventory/" + listing.getInventoryItem().getId();
        }
        return liveUrl;
    }

    private static boolean usableHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://");
    }

    private static boolean brokenBrickLinkStoreUrl(String url) {
        return url.contains("store.page?p=#");
    }

    private void email(BuyingOpportunity opportunity) {
        String body = opportunity.getBody() + (opportunity.getUrl() == null ? "" : "\n\n" + opportunity.getUrl());
        if (alertMailer.sendQuietly("[The Timeless Vault] " + opportunity.getTitle(), body)) {
            opportunity.setEmailedAt(Instant.now());
            opportunities.save(opportunity);
        }
    }
}
