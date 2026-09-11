package com.thetimelessvault.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DescriptionHtml {

    private static final Pattern BOX_GRADE_SCORE = Pattern.compile("(?i)box\\s*grade\\s*:?\\s*(\\d+)");

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "ul", "ol", "li", "h2", "h3", "a", "span")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https");

    private DescriptionHtml() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String cleaned = Jsoup.clean(html, "", SAFELIST, new Document.OutputSettings().prettyPrint(false));
        String plain = Jsoup.parse(cleaned).text().trim();
        return plain.isEmpty() ? "" : cleaned;
    }

    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    public static String forShopify(String html) {
        return sanitize(html);
    }

    public static String forEbayListing(String html) {
        return withoutExternalGradingLink(sanitize(html));
    }

    public static String forEbayProduct(String html) {
        String sanitized = withoutExternalGradingLink(sanitize(html));
        return sanitized.length() <= 4000 ? sanitized : sanitized.substring(0, 4000);
    }

    /**
     * eBay disallows off-site links in listing HTML. Shopify keeps the grading-page sentence.
     */
    private static String withoutExternalGradingLink(String html) {
        if (html == null || html.isBlank()) {
            return html == null ? "" : html;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (Element paragraph : new ArrayList<>(doc.select("p"))) {
            if (paragraph.text().toLowerCase().contains("for more details about our grading system")) {
                paragraph.remove();
            }
        }
        return doc.body().html();
    }

    public static String forBrickLink(String html) {
        String plain = toPlainText(html);
        return plain.length() <= 255 ? plain : plain.substring(0, 255);
    }

    /**
     * BrickLink short description: condition remainder + box grade + photo ask.
     * Matches the admin UI autocomplete in brickLinkShortDescriptionFromHtml.
     */
    public static String shortDescriptionFromListingHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(html);
        String condition = null;
        String boxGrade = null;
        for (Element paragraph : doc.select("p")) {
            String text = paragraph.text().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            if (condition == null && isConditionParagraph(text)) {
                condition = text;
            } else if (boxGrade == null && text.regionMatches(true, 0, "box grade:", 0, "box grade:".length())) {
                boxGrade = text;
            }
        }
        if (condition == null || boxGrade == null) {
            return "";
        }
        String remainder = condition.replaceFirst("(?i)^condition:\\s*new sealed in box\\s*", "").trim();
        String text = (remainder + " " + boxGrade + " Ask for more photos!").replaceAll("\\s+", " ").trim();
        return text.length() <= 255 ? text : text.substring(0, 255);
    }

    /**
     * Score from a listing description ("Box Grade: 8/10 …"), or null when none is present.
     */
    public static Integer inferBoxGradeScore(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element paragraph : doc.select("p")) {
            String text = paragraph.text().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            if (text.regionMatches(true, 0, "box grade:", 0, "box grade:".length())) {
                return scoreIn(text);
            }
        }
        return scoreIn(toPlainText(html));
    }

    private static Integer scoreIn(String text) {
        Matcher matcher = BOX_GRADE_SCORE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean isConditionParagraph(String text) {
        return text.regionMatches(true, 0, "condition:", 0, "condition:".length())
                || text.regionMatches(true, 0, "used 100% complete:", 0, "used 100% complete:".length())
                || text.regionMatches(true, 0, "used missing parts:", 0, "used missing parts:".length());
    }
}
