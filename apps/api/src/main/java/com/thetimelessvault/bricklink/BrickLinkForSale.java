package com.thetimelessvault.bricklink;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrickLinkForSale {

    private static final Pattern ITEM_ID = Pattern.compile("idItem:\\s*(\\d+)");
    private static final Pattern USD_PRICE = Pattern.compile("US\\s*\\$\\s*([0-9,]+(?:\\.[0-9]+)?)");

    private BrickLinkForSale() {
    }

    public static Long parseItemId(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher matcher = ITEM_ID.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    public static BigDecimal parseUsdPrice(String displayPrice) {
        if (displayPrice == null || displayPrice.isBlank()) {
            return null;
        }
        Matcher matcher = USD_PRICE.matcher(displayPrice);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1).replace(",", ""));
    }

    public static List<Lot> parseLots(JsonNode root) {
        return parseLots(root, null);
    }

    public static List<Lot> parseLots(JsonNode root, String setNumber) {
        List<Lot> lots = new ArrayList<>();
        if (root == null) {
            return lots;
        }
        for (JsonNode row : root.path("list")) {
            Lot lot = toLot(row, setNumber);
            if (lot != null) {
                lots.add(lot);
            }
        }
        return lots;
    }

    public static boolean isNewSealed(JsonNode row) {
        return "N".equalsIgnoreCase(row.path("codeNew").asText(""))
                && "S".equalsIgnoreCase(row.path("codeComplete").asText(""));
    }

    private static Lot toLot(JsonNode row, String setNumber) {
        if (row == null || row.isMissingNode() || !isNewSealed(row)) {
            return null;
        }
        long inventoryId = row.path("idInv").asLong(0);
        if (inventoryId <= 0) {
            return null;
        }
        String seller = firstText(row, "strStorename", "strSellerUsername");
        String description = blankToNull(row.path("strDesc").asText(null));
        return new Lot(
                inventoryId,
                description,
                parseUsdPrice(row.path("mDisplaySalePrice").asText(null)),
                row.path("n4Qty").asInt(1),
                "New Sealed",
                seller,
                firstText(row, "strSellerCountryName", "strSellerCountryCode"),
                imageUrl(row, setNumber),
                storeUrl(row.path("strSellerUsername").asText(null), inventoryId)
        );
    }

    static String storeUrl(String username, long inventoryId) {
        if (username == null || username.isBlank()) {
            return "https://www.bricklink.com/v2/inventory_detail.page?invID=" + inventoryId;
        }
        return "https://store.bricklink.com/"
                + URLEncoder.encode(username.trim(), StandardCharsets.UTF_8)
                + "?itemID=" + inventoryId
                + "#/shop";
    }

    static String imageUrl(JsonNode row, String setNumber) {
        String custom = row.path("strInvImgUrl").asText("");
        if (!custom.isBlank()) {
            if (custom.startsWith("//")) {
                return "https:" + custom;
            }
            if (custom.startsWith("/")) {
                return "https://img.bricklink.com" + custom;
            }
            return custom;
        }
        long imageId = row.path("idInvImg").asLong(0);
        if (imageId > 0) {
            String ext = "J".equalsIgnoreCase(row.path("typeInvImg").asText("J")) ? ".jpg" : ".gif";
            return "https://img.bricklink.com/myImg/Thumb/" + imageId + ext;
        }
        if (setNumber == null || setNumber.isBlank()) {
            return null;
        }
        return "https://img.bricklink.com/ItemImage/SN/0/" + setNumber.trim() + ".png";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstText(JsonNode row, String... fields) {
        for (String field : fields) {
            String value = row.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record Lot(
            long inventoryId,
            String title,
            BigDecimal price,
            int quantity,
            String condition,
            String seller,
            String sellerCountry,
            String imageUrl,
            String url
    ) {
        public String fingerprint() {
            return String.valueOf(inventoryId);
        }
    }
}
