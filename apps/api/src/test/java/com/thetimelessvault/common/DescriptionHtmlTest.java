package com.thetimelessvault.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptionHtmlTest {

    @Test
    void keepsSafeMarkup() {
        String html = "<p>Factory <strong>sealed</strong> set.</p><ul><li>NISB</li></ul>";
        String cleaned = DescriptionHtml.sanitize(html);
        assertTrue(cleaned.contains("<strong>sealed</strong>"));
        assertTrue(cleaned.contains("<li>NISB</li>"));
    }

    @Test
    void keepsSpans() {
        String cleaned = DescriptionHtml.sanitize("<p><strong>Set number:</strong><span>10236</span></p>");
        assertTrue(cleaned.contains("<span>10236</span>"));
    }

    @Test
    void stripsScripts() {
        String cleaned = DescriptionHtml.sanitize("<p>Safe</p><script>alert(1)</script>");
        assertFalse(cleaned.contains("script"));
        assertTrue(cleaned.contains("Safe"));
    }

    @Test
    void brickLinkIsPlainTextAndCapped() {
        String html = "<p>" + "A".repeat(300) + "</p>";
        String plain = DescriptionHtml.forBrickLink(html);
        assertEquals(255, plain.length());
        assertFalse(plain.contains("<p>"));
    }

    @Test
    void ebayDropsGradingPageSentenceAndShopifyKeepsIt() {
        String html = """
                <p><strong>Pieces: </strong>100</p>
                <p><span>For more details about our grading system, </span>\
                <a href="https://www.thetimelessvault.shop/pages/grading">click here</a><span>.</span></p>
                <p><span><strong>Condition Disclaimer:</strong> Please review photos.</span></p>
                """;

        String shopify = DescriptionHtml.forShopify(html);
        assertTrue(shopify.contains("For more details about our grading system"));
        assertTrue(shopify.contains("thetimelessvault.shop/pages/grading"));

        String ebayListing = DescriptionHtml.forEbayListing(html);
        assertFalse(ebayListing.contains("For more details about our grading system"));
        assertFalse(ebayListing.contains("click here"));
        assertFalse(ebayListing.contains("thetimelessvault.shop/pages/grading"));
        assertTrue(ebayListing.contains("Pieces"));
        assertTrue(ebayListing.contains("Condition Disclaimer"));

        String ebayProduct = DescriptionHtml.forEbayProduct(html);
        assertFalse(ebayProduct.contains("For more details about our grading system"));
        assertTrue(ebayProduct.contains("Condition Disclaimer"));
    }
}
