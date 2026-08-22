package com.thetimelessvault.ebay;

import com.thetimelessvault.common.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EbayActiveListings {

    private static final Pattern ITEM = Pattern.compile("(?s)<(?:\\w+:)?Item>(.*?)</(?:\\w+:)?Item>");
    private static final Pattern TAG = Pattern.compile("(?s)<(?:\\w+:)?%s(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?%s>");
    private static final Pattern PICTURE = Pattern.compile("(?s)<(?:\\w+:)?(?:PictureURL|GalleryURL)>(.*?)</(?:\\w+:)?(?:PictureURL|GalleryURL)>");
    private static final Pattern TOTAL_PAGES = Pattern.compile("<(?:\\w+:)?TotalNumberOfPages>(\\d+)</(?:\\w+:)?TotalNumberOfPages>");

    private EbayActiveListings() {
    }

    public record Page(List<EbayActiveListing> listings, int totalPages) {
    }

    public static Page parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay GetMyeBaySelling returned an empty response");
        }
        if (xml.contains("<Ack>Failure</Ack>") || xml.contains("<Ack>PartialFailure</Ack>")) {
            Matcher message = Pattern.compile("<(?:LongMessage|ShortMessage)>([^<]+)</(?:LongMessage|ShortMessage)>").matcher(xml);
            String detail = message.find() ? unescape(message.group(1)) : xml;
            throw new ApiException(HttpStatus.BAD_GATEWAY, "eBay GetMyeBaySelling failed: " + detail);
        }
        String active = slice(xml, "ActiveList");
        List<EbayActiveListing> listings = new ArrayList<>();
        Matcher items = ITEM.matcher(active);
        while (items.find()) {
            EbayActiveListing listing = fromItem(items.group(1));
            if (listing != null) {
                listings.add(listing);
            }
        }
        Matcher pages = TOTAL_PAGES.matcher(active.isBlank() ? xml : active);
        int totalPages = pages.find() ? Integer.parseInt(pages.group(1)) : 1;
        return new Page(listings, Math.max(1, totalPages));
    }

    static EbayActiveListing fromItem(String itemXml) {
        String listingId = text(itemXml, "ItemID");
        if (listingId == null || listingId.isBlank()) {
            return null;
        }
        String title = text(itemXml, "Title");
        String sku = text(itemXml, "SKU");
        String description = text(itemXml, "Description");
        String quantityText = firstNonBlank(text(itemXml, "QuantityAvailable"), text(itemXml, "Quantity"));
        int quantity = parseInt(quantityText, 1);
        BigDecimal price = parseMoney(text(itemXml, "CurrentPrice"));
        return new EbayActiveListing(
                listingId,
                blankToNull(sku),
                title == null ? "" : title,
                description,
                price,
                Math.max(0, quantity),
                pictures(itemXml),
                text(itemXml, "ConditionID"),
                text(itemXml, "ConditionDisplayName")
        );
    }

    private static List<String> pictures(String itemXml) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher pictures = PICTURE.matcher(itemXml);
        while (pictures.find()) {
            String url = unescape(stripCdata(pictures.group(1))).trim();
            if (EbayClient.isPublicHttpsImageUrl(url)) {
                urls.add(url);
            }
        }
        return List.copyOf(urls);
    }

    private static String slice(String xml, String tag) {
        Matcher matcher = Pattern.compile("(?s)<(?:\\w+:)?" + tag + ">(.*?)</(?:\\w+:)?" + tag + ">").matcher(xml);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String text(String xml, String tag) {
        Matcher matcher = Pattern.compile(TAG.pattern().formatted(tag, tag)).matcher(xml);
        if (!matcher.find()) {
            return null;
        }
        return unescape(stripCdata(matcher.group(1))).trim();
    }

    private static String stripCdata(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>")) {
            return trimmed.substring(9, trimmed.length() - 3);
        }
        return trimmed;
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
