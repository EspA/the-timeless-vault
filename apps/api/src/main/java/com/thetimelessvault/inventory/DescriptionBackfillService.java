package com.thetimelessvault.inventory;

import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.common.DescriptionHtml;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.shopify.ShopifyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DescriptionBackfillService {

    private static final Logger log = LoggerFactory.getLogger(DescriptionBackfillService.class);
    private static final Pattern SHOPIFY_HANDLE = Pattern.compile("/products/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final ShopifyClient shopify;
    private final BrickLinkClient brickLink;
    private final TransactionTemplate transactions;

    public DescriptionBackfillService(
            InventoryItemRepository items,
            ChannelListingRepository listings,
            ShopifyClient shopify,
            BrickLinkClient brickLink,
            PlatformTransactionManager transactionManager
    ) {
        this.items = items;
        this.listings = listings;
        this.shopify = shopify;
        this.brickLink = brickLink;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Report run(boolean dryRun) {
        List<InventoryItem> candidates = items.findAllWithCatalog().stream()
                .filter(DescriptionBackfillService::needsBackfill)
                .toList();
        List<Row> rows = new ArrayList<>();
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        for (InventoryItem item : candidates) {
            try {
                Row row = dryRun ? preview(item) : transactions.execute(status -> apply(item));
                rows.add(row);
                switch (row.action()) {
                    case "UPDATE" -> updated += 1;
                    case "SKIP" -> skipped += 1;
                    default -> failed += 1;
                }
            } catch (RuntimeException e) {
                failed += 1;
                log.warn("Description backfill failed for {}: {}", item.getSku(), e.getMessage());
                rows.add(row("FAIL", item, null, null, e.getMessage()));
            }
        }
        return new Report(dryRun, candidates.size(), updated, skipped, failed, rows);
    }

    private Row preview(InventoryItem item) {
        Draft draft = load(item);
        String action = draft.filled() ? "UPDATE" : "SKIP";
        return row(action, item, draft.htmlDescription, draft.shortDescription, draft.message);
    }

    private Row apply(InventoryItem item) {
        InventoryItem current = items.findWithCatalogById(item.getId()).orElse(item);
        if (!needsBackfill(current)) {
            return row("SKIP", current, null, null, "Already has descriptions");
        }
        Draft draft = load(current);
        if (!draft.filled()) {
            return row("SKIP", current, draft.htmlDescription, draft.shortDescription, draft.message);
        }
        if (draft.htmlDescription != null) {
            current.setDescription(draft.htmlDescription);
        }
        if (draft.shortDescription != null) {
            current.setShortDescription(draft.shortDescription);
        }
        current.touch();
        items.save(current);
        return row("UPDATE", current, draft.htmlDescription, draft.shortDescription, draft.message);
    }

    private Draft load(InventoryItem item) {
        String html = null;
        String htmlNote = null;
        Optional<ChannelListing> shopifyListing = linked(item, Platform.SHOPIFY);
        if (shopifyListing.isEmpty()) {
            htmlNote = "Shopify listing not linked";
        } else {
            try {
                ChannelListing listing = shopifyListing.get();
                String fetched = shopify.productDescriptionHtml(listing.getExternalId(), handleFrom(listing.getLiveUrl()));
                if (blank(fetched)) {
                    htmlNote = "Shopify description was empty";
                } else {
                    html = fetched;
                }
            } catch (RuntimeException e) {
                htmlNote = "Shopify: " + e.getMessage();
            }
        }

        String shortDescription = null;
        String shortNote = null;
        Optional<ChannelListing> brickLinkListing = linked(item, Platform.BRICKLINK)
                .filter(listing -> listing.getExternalId() != null && !listing.getExternalId().isBlank());
        if (brickLinkListing.isEmpty()) {
            shortNote = "BrickLink listing not linked";
        } else {
            try {
                String fetched = DescriptionHtml.forBrickLink(
                        brickLink.getInventory(brickLinkListing.get().getExternalId()).path("description").asText("")
                );
                if (blank(fetched)) {
                    shortNote = "BrickLink description was empty";
                } else {
                    shortDescription = fetched;
                }
            } catch (RuntimeException e) {
                shortNote = "BrickLink: " + e.getMessage();
            }
        }

        List<String> notes = new ArrayList<>();
        if (html != null) {
            notes.add("Shopify HTML");
        } else if (htmlNote != null) {
            notes.add(htmlNote);
        }
        if (shortDescription != null) {
            notes.add("BrickLink short description");
        } else if (shortNote != null) {
            notes.add(shortNote);
        }
        String message = notes.isEmpty() ? "Nothing to copy" : String.join("; ", notes);
        return new Draft(html, shortDescription, message);
    }

    private Optional<ChannelListing> linked(InventoryItem item, Platform platform) {
        return listings.findByInventoryItemIdAndPlatform(item.getId(), platform)
                .filter(listing -> listing.getExternalId() != null && !listing.getExternalId().isBlank()
                        || listing.getLiveUrl() != null && !listing.getLiveUrl().isBlank());
    }

    static boolean needsBackfill(InventoryItem item) {
        return blank(item.getDescription()) && blank(item.getShortDescription());
    }

    static boolean blank(String value) {
        return DescriptionHtml.toPlainText(value).isBlank();
    }

    static String handleFrom(String liveUrl) {
        if (liveUrl == null || liveUrl.isBlank()) {
            return null;
        }
        Matcher matcher = SHOPIFY_HANDLE.matcher(liveUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Row row(String action, InventoryItem item, String html, String shortDescription, String message) {
        String setNumber = item.getCatalogItem() == null ? null : item.getCatalogItem().getSetNumber();
        return new Row(action, item.getSku(), setNumber, html, shortDescription, message);
    }

    private record Draft(String htmlDescription, String shortDescription, String message) {
        boolean filled() {
            return htmlDescription != null || shortDescription != null;
        }
    }

    public record Report(
            boolean dryRun,
            int candidates,
            int updated,
            int skipped,
            int failed,
            List<Row> rows
    ) {
    }

    public record Row(
            String action,
            String sku,
            String setNumber,
            String htmlDescription,
            String shortDescription,
            String message
    ) {
    }
}
