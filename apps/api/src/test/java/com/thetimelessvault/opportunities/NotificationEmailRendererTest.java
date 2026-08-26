package com.thetimelessvault.opportunities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationEmailRendererTest {

    private static final Instant SCANNED = Instant.parse("2026-08-19T23:54:00Z");

    @Test
    void buyingOpportunityIncludesTagPricePercentAndListingLink() {
        NotificationEmail email = new NotificationEmail(
                BuyingOpportunity.TYPE_BUYING_OPPORTUNITY,
                "75017-1",
                "Duel on Geonosis",
                "https://i.ebayimg.com/photo.jpg",
                "$315.00",
                "-12%",
                "https://www.ebay.com/itm/123",
                "https://admin.thetimelessvault.com/inventory/abc",
                "EBAY",
                "brickshop",
                "Feedback 1,842 / 99.8%",
                SCANNED,
                null
        );

        String html = NotificationEmailRenderer.html(email);
        assertTrue(html.contains("BUYING OPPORTUNITY"));
        assertTrue(html.contains("75017-1 / Duel on Geonosis"));
        assertTrue(html.contains("$315.00"));
        assertTrue(html.contains("(-12%)"));
        assertTrue(html.contains("https://i.ebayimg.com/photo.jpg"));
        assertTrue(html.contains("Open listing"));
        assertTrue(html.contains("https://www.ebay.com/itm/123"));
        assertTrue(html.contains("Scanned Aug 19, 2026"));
        assertTrue(html.contains("brickshop"));
        assertTrue(html.contains("Feedback 1,842 / 99.8%"));
        assertTrue(html.contains("ebay-logo.png"));
        assertTrue(html.contains("alt=\"eBay\""));
        assertFalse(html.contains(">eBay</span>"));
        assertFalse(html.contains("Adjust price"));
        assertEquals(
                "[The Timeless Vault] BUYING OPPORTUNITY · eBay · 75017-1 / Duel on Geonosis",
                NotificationEmailRenderer.subject(email)
        );
        assertTrue(NotificationEmailRenderer.text(email).contains("Scanned Aug 19, 2026"));
        assertTrue(NotificationEmailRenderer.text(email).contains("eBay"));
        assertTrue(NotificationEmailRenderer.text(email).contains("Seller brickshop"));
        assertTrue(NotificationEmailRenderer.text(email).contains("Feedback 1,842 / 99.8%"));
    }

    @Test
    void bricklinkBuyingOpportunityShowsCountry() {
        NotificationEmail email = new NotificationEmail(
                BuyingOpportunity.TYPE_BUYING_OPPORTUNITY,
                "75017-1",
                "Duel on Geonosis",
                null,
                "$280.00",
                "-10%",
                "https://www.bricklink.com/v2/catalog/catalogitem.page?S=75017-1",
                null,
                "BRICKLINK",
                "kaden50",
                "Country United Kingdom",
                SCANNED,
                null
        );

        String html = NotificationEmailRenderer.html(email);
        assertTrue(html.contains("bricklink-logo.png"));
        assertTrue(html.contains("alt=\"BrickLink\""));
        assertTrue(html.contains("kaden50"));
        assertTrue(html.contains("Country United Kingdom"));
        assertFalse(html.contains("Feedback"));
        assertTrue(NotificationEmailRenderer.text(email).contains("BrickLink"));
        assertTrue(NotificationEmailRenderer.text(email).contains("Seller kaden50"));
        assertTrue(NotificationEmailRenderer.text(email).contains("Country United Kingdom"));
        assertEquals(
                "[The Timeless Vault] BUYING OPPORTUNITY · BrickLink · 75017-1 / Duel on Geonosis",
                NotificationEmailRenderer.subject(email)
        );
    }

    @Test
    void priceGuardAddsInventoryLink() {
        NotificationEmail email = new NotificationEmail(
                BuyingOpportunity.TYPE_PRICE_HIGH,
                "10195-1",
                "Republic Dropship with AT-OT Walker",
                null,
                "$890.00",
                "+18%",
                "https://www.ebay.com/itm/9",
                "https://admin.thetimelessvault.com/inventory/item-1",
                "EBAY",
                "ignored-seller",
                "Feedback 99 / 100%",
                SCANNED,
                null
        );

        String html = NotificationEmailRenderer.html(email);
        assertTrue(html.contains("PRICE HIGH"));
        assertTrue(html.contains("ebay-logo.png"));
        assertTrue(html.contains("alt=\"eBay\""));
        assertTrue(html.contains("Open listing"));
        assertTrue(html.contains("Adjust price"));
        assertTrue(html.contains("https://admin.thetimelessvault.com/inventory/item-1"));
        assertFalse(html.contains("ignored-seller"));
        assertFalse(html.contains("Feedback 99 / 100%"));
    }

    @Test
    void formatsPercentAgainstMedian() {
        assertEquals("-12%", NotificationEmailRenderer.percentVsMedian(new BigDecimal("315.00"), new BigDecimal("358.00")));
        assertEquals("+18%", NotificationEmailRenderer.percentVsMedian(new BigDecimal("890.00"), new BigDecimal("754.00")));
        assertEquals("$315.00", NotificationEmailRenderer.money(new BigDecimal("315.00")));
        assertEquals("Feedback 1,842 / 99.8%", NotificationEmailRenderer.feedback(1842, "99.8"));
        assertEquals("Feedback 12 / 100%", NotificationEmailRenderer.feedback(12, "100%"));
        assertEquals(
                "https://admin.thetimelessvault.com/email/ebay-logo.png",
                NotificationEmailRenderer.platformLogoSrc("EBAY", "https://admin.thetimelessvault.com/")
        );
        assertEquals("/email/bricklink-logo.png", NotificationEmailRenderer.platformLogoSrc("BRICKLINK", null));
    }

    @Test
    void escapesHtmlInSetName() {
        NotificationEmail email = new NotificationEmail(
                BuyingOpportunity.TYPE_PRICE_LOW,
                "1",
                "Foo <script>",
                null,
                "$1.00",
                "",
                "https://example.com",
                "https://example.com/inventory/1",
                null,
                null,
                null,
                SCANNED,
                null
        );
        assertTrue(NotificationEmailRenderer.html(email).contains("Foo &lt;script&gt;"));
        assertFalse(NotificationEmailRenderer.html(email).contains("Foo <script>"));
    }

    @Test
    void scanFailureIncludesReasonAndMarketLink() {
        NotificationEmail email = new NotificationEmail(
                BuyingOpportunity.TYPE_SCAN_FAILED,
                "75017-1",
                "Duel on Geonosis",
                null,
                null,
                null,
                "https://admin.thetimelessvault.com/market/abc",
                null,
                "EBAY",
                null,
                null,
                SCANNED,
                "eBay search failed."
        );

        String html = NotificationEmailRenderer.html(email);
        assertTrue(html.contains("SCAN FAILED"));
        assertTrue(html.contains("eBay search failed."));
        assertTrue(html.contains("Open market"));
        assertTrue(html.contains("https://admin.thetimelessvault.com/market/abc"));
        assertEquals(
                "[The Timeless Vault] SCAN FAILED · eBay · 75017-1 / Duel on Geonosis",
                NotificationEmailRenderer.subject(email)
        );
        assertTrue(NotificationEmailRenderer.text(email).contains("eBay search failed."));
        assertTrue(NotificationEmailRenderer.text(email).contains("Open market:"));
    }

    @Test
    void orderDeliveredIncludesOpenOrderLinkAndTracking() {
        NotificationEmail email = new NotificationEmail(
                BuyingOpportunity.TYPE_ORDER_DELIVERED,
                "75192-1",
                "Millennium Falcon",
                null,
                "$899.99",
                null,
                "https://admin.thetimelessvault.com/orders/abc",
                "https://admin.thetimelessvault.com/inventory/item-1",
                "EBAY",
                null,
                null,
                SCANNED,
                "Tracking 9400111 · USPS"
        );

        String html = NotificationEmailRenderer.html(email);
        assertTrue(html.contains("ORDER DELIVERED"));
        assertTrue(html.contains("75192-1 / Millennium Falcon"));
        assertTrue(html.contains("$899.99"));
        assertTrue(html.contains("Open order"));
        assertTrue(html.contains("https://admin.thetimelessvault.com/orders/abc"));
        assertTrue(html.contains("Tracking 9400111 · USPS"));
        assertFalse(html.contains("Adjust price"));
        assertEquals(
                "[The Timeless Vault] ORDER DELIVERED · eBay · 75192-1 / Millennium Falcon",
                NotificationEmailRenderer.subject(email)
        );
        assertTrue(NotificationEmailRenderer.text(email).contains("Open order:"));
        assertTrue(NotificationEmailRenderer.text(email).contains("Tracking 9400111 · USPS"));
    }

    @Test
    void purchaseOrderDeliveredIncludesOpenPurchaseOrderLinkAndTracking() {
        NotificationEmail email = new NotificationEmail(
                BuyingOpportunity.TYPE_PURCHASE_ORDER_DELIVERED,
                "PO-1",
                "Brick Depot",
                null,
                "$200.00",
                null,
                "https://admin.thetimelessvault.com/purchase-orders/abc",
                null,
                "UPS",
                null,
                null,
                SCANNED,
                "Tracking 1Z123 · UPS"
        );

        String html = NotificationEmailRenderer.html(email);
        assertTrue(html.contains("PURCHASE ORDER DELIVERED"));
        assertTrue(html.contains("PO-1 / Brick Depot"));
        assertTrue(html.contains("$200.00"));
        assertTrue(html.contains("Open purchase order"));
        assertTrue(html.contains("https://admin.thetimelessvault.com/purchase-orders/abc"));
        assertTrue(html.contains("Tracking 1Z123 · UPS"));
        assertEquals(
                "[The Timeless Vault] PURCHASE ORDER DELIVERED · UPS · PO-1 / Brick Depot",
                NotificationEmailRenderer.subject(email)
        );
        assertTrue(NotificationEmailRenderer.text(email).contains("Open purchase order:"));
    }
}
