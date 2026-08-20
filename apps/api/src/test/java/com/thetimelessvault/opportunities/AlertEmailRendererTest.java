package com.thetimelessvault.opportunities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertEmailRendererTest {

    private static final Instant SCANNED = Instant.parse("2026-08-19T23:54:00Z");

    @Test
    void buyingOpportunityIncludesTagPricePercentAndListingLink() {
        AlertEmail email = new AlertEmail(
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
                SCANNED
        );

        String html = AlertEmailRenderer.html(email);
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
                AlertEmailRenderer.subject(email)
        );
        assertTrue(AlertEmailRenderer.text(email).contains("Scanned Aug 19, 2026"));
        assertTrue(AlertEmailRenderer.text(email).contains("eBay"));
        assertTrue(AlertEmailRenderer.text(email).contains("Seller brickshop"));
        assertTrue(AlertEmailRenderer.text(email).contains("Feedback 1,842 / 99.8%"));
    }

    @Test
    void bricklinkBuyingOpportunityShowsCountry() {
        AlertEmail email = new AlertEmail(
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
                SCANNED
        );

        String html = AlertEmailRenderer.html(email);
        assertTrue(html.contains("bricklink-logo.png"));
        assertTrue(html.contains("alt=\"BrickLink\""));
        assertTrue(html.contains("kaden50"));
        assertTrue(html.contains("Country United Kingdom"));
        assertFalse(html.contains("Feedback"));
        assertTrue(AlertEmailRenderer.text(email).contains("BrickLink"));
        assertTrue(AlertEmailRenderer.text(email).contains("Seller kaden50"));
        assertTrue(AlertEmailRenderer.text(email).contains("Country United Kingdom"));
        assertEquals(
                "[The Timeless Vault] BUYING OPPORTUNITY · BrickLink · 75017-1 / Duel on Geonosis",
                AlertEmailRenderer.subject(email)
        );
    }

    @Test
    void priceGuardAddsInventoryLink() {
        AlertEmail email = new AlertEmail(
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
                SCANNED
        );

        String html = AlertEmailRenderer.html(email);
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
        assertEquals("-12%", AlertEmailRenderer.percentVsMedian(new BigDecimal("315.00"), new BigDecimal("358.00")));
        assertEquals("+18%", AlertEmailRenderer.percentVsMedian(new BigDecimal("890.00"), new BigDecimal("754.00")));
        assertEquals("$315.00", AlertEmailRenderer.money(new BigDecimal("315.00")));
        assertEquals("Feedback 1,842 / 99.8%", AlertEmailRenderer.feedback(1842, "99.8"));
        assertEquals("Feedback 12 / 100%", AlertEmailRenderer.feedback(12, "100%"));
        assertEquals(
                "https://admin.thetimelessvault.com/email/ebay-logo.png",
                AlertEmailRenderer.platformLogoSrc("EBAY", "https://admin.thetimelessvault.com/")
        );
        assertEquals("/email/bricklink-logo.png", AlertEmailRenderer.platformLogoSrc("BRICKLINK", null));
    }

    @Test
    void escapesHtmlInSetName() {
        AlertEmail email = new AlertEmail(
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
                SCANNED
        );
        assertTrue(AlertEmailRenderer.html(email).contains("Foo &lt;script&gt;"));
        assertFalse(AlertEmailRenderer.html(email).contains("Foo <script>"));
    }
}
