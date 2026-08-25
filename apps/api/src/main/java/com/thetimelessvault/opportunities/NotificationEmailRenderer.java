package com.thetimelessvault.opportunities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class NotificationEmailRenderer {

    private static final String INK = "#1b1410";
    private static final String PAPER = "#f4efe4";
    private static final String CARD = "#fffdf8";
    private static final String GOLD = "#b8892d";
    private static final String GOLD_DEEP = "#8c6818";
    private static final String LINE = "#d8cbb3";
    private static final String BADGE_OK = "#d9ead3";
    private static final String BADGE_OK_INK = "#2f5d32";
    private static final String BADGE_PRICE = "#f3c07a";
    private static final String BADGE_FAIL = "#f4c7c3";
    private static final String BADGE_FAIL_INK = "#7a1f1a";
    private static final String GOLD_INK = "#1b1410";
    private static final String SLATE = "#3d4a52";
    private static final ZoneId SCAN_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter SCAN_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a z", Locale.US)
            .withZone(SCAN_ZONE);

    private NotificationEmailRenderer() {
    }

    public static String subject(NotificationEmail email) {
        String heading = heading(email);
        String platform = platformLabel(email.platform());
        StringBuilder subject = new StringBuilder("[The Timeless Vault] ").append(email.typeLabel());
        if (!platform.isBlank()) {
            subject.append(" · ").append(platform);
        }
        if (!heading.isBlank()) {
            subject.append(" · ").append(heading);
        }
        return subject.toString();
    }

    public static String text(NotificationEmail email) {
        StringBuilder body = new StringBuilder();
        body.append(email.typeLabel()).append("\n\n");
        String heading = heading(email);
        if (!heading.isBlank()) {
            body.append(heading).append("\n");
        }
        String scanned = formatScanDate(email.scannedAt());
        if (!scanned.isBlank()) {
            body.append("Scanned ").append(scanned).append("\n");
        }
        String platform = platformLabel(email.platform());
        if (!platform.isBlank()) {
            body.append(platform).append("\n");
        }
        if (email.buyingOpportunity()) {
            if (email.sellerName() != null && !email.sellerName().isBlank()) {
                body.append("Seller ").append(email.sellerName()).append("\n");
            }
            if (email.sellerMeta() != null && !email.sellerMeta().isBlank()) {
                body.append(email.sellerMeta()).append("\n");
            }
        }
        if (!heading.isBlank() || !scanned.isBlank() || !platform.isBlank()
                || (email.buyingOpportunity() && (
                (email.sellerName() != null && !email.sellerName().isBlank())
                        || (email.sellerMeta() != null && !email.sellerMeta().isBlank())))) {
            body.append("\n");
        }
        if (email.detail() != null && !email.detail().isBlank()) {
            body.append(email.detail()).append("\n\n");
        }
        if (email.price() != null && !email.price().isBlank()) {
            body.append(email.price());
            if (email.percentVsMedian() != null && !email.percentVsMedian().isBlank()) {
                body.append(" (").append(email.percentVsMedian()).append(")");
            }
            body.append("\n\n");
        }
        if (email.listingUrl() != null && !email.listingUrl().isBlank()) {
            body.append(scanFailedLinkLabel(email)).append(":\n").append(email.listingUrl()).append("\n");
        }
        if (email.priceGuard() && email.inventoryUrl() != null && !email.inventoryUrl().isBlank()
                && !email.inventoryUrl().equals(email.listingUrl())) {
            body.append("Adjust price:\n").append(email.inventoryUrl()).append("\n");
        }
        return body.toString().trim() + "\n";
    }

    public static String html(NotificationEmail email) {
        return html(email, null);
    }

    public static String html(NotificationEmail email, String assetBaseUrl) {
        String badgeBg = email.scanFailed() ? BADGE_FAIL : email.buyingOpportunity() || email.orderAlert() ? BADGE_OK : BADGE_PRICE;
        String badgeInk = email.scanFailed() ? BADGE_FAIL_INK : email.buyingOpportunity() || email.orderAlert() ? BADGE_OK_INK : GOLD_INK;
        String heading = heading(email);
        StringBuilder priceHtml = new StringBuilder();
        if (email.price() != null && !email.price().isBlank()) {
            priceHtml.append("<div style=\"font-family:Georgia,'Cormorant Garamond',serif;font-size:32px;line-height:1.2;")
                    .append("color:").append(INK).append(";font-weight:600;letter-spacing:0.02em;\">")
                    .append(esc(email.price()));
            if (email.percentVsMedian() != null && !email.percentVsMedian().isBlank()) {
                priceHtml.append(" <span style=\"font-family:'Source Sans 3',Arial,sans-serif;font-size:18px;")
                        .append("color:").append(GOLD_DEEP).append(";font-weight:500;\">(")
                        .append(esc(email.percentVsMedian()))
                        .append(")</span>");
            }
            priceHtml.append("</div>");
        }
        if (email.detail() != null && !email.detail().isBlank()) {
            priceHtml.append("<div style=\"font-family:'Source Sans 3',Arial,sans-serif;font-size:16px;line-height:1.45;")
                    .append("color:").append(INK).append(";\">")
                    .append(esc(email.detail()))
                    .append("</div>");
        }
        String photo = "";
        if (email.photoUrl() != null && !email.photoUrl().isBlank()) {
            photo = "<tr><td style=\"padding:0 28px 20px;\">"
                    + "<img src=\"" + esc(email.photoUrl()) + "\" alt=\"" + esc(heading) + "\" width=\"240\" "
                    + "style=\"display:block;max-width:240px;width:100%;height:auto;border-radius:12px;"
                    + "border:1px solid " + LINE + ";background:#fff;\" />"
                    + "</td></tr>";
        }
        StringBuilder links = new StringBuilder();
        if (email.listingUrl() != null && !email.listingUrl().isBlank()) {
            links.append(button(scanFailedLinkLabel(email), email.listingUrl(), GOLD, GOLD_INK, false));
        }
        if (email.priceGuard() && email.inventoryUrl() != null && !email.inventoryUrl().isBlank()
                && !email.inventoryUrl().equals(email.listingUrl())) {
            links.append(button("Adjust price", email.inventoryUrl(), "transparent", INK, true));
        }
        String scanned = formatScanDate(email.scannedAt());
        String scanDate = scanned.isBlank() ? ""
                : "<tr><td style=\"padding:0 28px 16px;font-family:'Source Sans 3',Arial,sans-serif;font-size:14px;color:"
                + SLATE + ";\">Scanned " + esc(scanned) + "</td></tr>";
        String seller = sellerHtml(email);
        return """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:%s;color:%s;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;padding:24px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%%;background:%s;border:1px solid %s;border-radius:18px;overflow:hidden;">
                          <tr>
                            <td style="padding:22px 28px 8px;font-family:'Source Sans 3',Arial,sans-serif;font-size:12px;letter-spacing:0.16em;text-transform:uppercase;color:%s;">
                              The Timeless Vault
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 28px 18px;">
                              <table role="presentation" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td valign="middle" style="padding:0;">
                                    <span style="display:inline-block;padding:4px 12px;border-radius:999px;font-family:'Source Sans 3',Arial,sans-serif;font-size:12px;font-weight:600;letter-spacing:0.04em;background:%s;color:%s;">%s</span>
                                  </td>
                                  %s
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 28px 8px;font-family:Georgia,'Cormorant Garamond',serif;font-size:28px;line-height:1.2;color:%s;font-weight:600;">
                              %s
                            </td>
                          </tr>
                          %s
                          %s
                          %s
                          <tr>
                            <td style="padding:0 28px 24px;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 28px 28px;">
                              %s
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                PAPER, INK, PAPER, CARD, LINE, GOLD,
                badgeBg, badgeInk, esc(email.typeLabel()), platformBadge(email, assetBaseUrl),
                INK, esc(heading),
                scanDate,
                seller,
                photo,
                priceHtml,
                links
        );
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    public static String percentVsMedian(BigDecimal amount, BigDecimal median) {
        if (amount == null || median == null || median.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        BigDecimal percent = amount.subtract(median)
                .multiply(BigDecimal.valueOf(100))
                .divide(median, 0, RoundingMode.HALF_UP);
        String sign = percent.signum() > 0 ? "+" : "";
        return sign + percent.toPlainString() + "%";
    }

    public static String formatScanDate(Instant scannedAt) {
        if (scannedAt == null) {
            return "";
        }
        return SCAN_DATE.format(scannedAt);
    }

    static String scanFailedLinkLabel(NotificationEmail email) {
        if (email.orderAlert()) {
            return "Open order";
        }
        if (!email.scanFailed()) {
            return "Open listing";
        }
        String url = email.listingUrl() == null ? "" : email.listingUrl();
        return url.contains("/scan-logs") ? "Open scan logs" : "Open market";
    }

    public static String platformLabel(String platform) {
        if (platform == null || platform.isBlank()) {
            return "";
        }
        return switch (platform.trim().toUpperCase(Locale.ROOT)) {
            case "EBAY" -> "eBay";
            case "BRICKLINK" -> "BrickLink";
            case "SHOPIFY" -> "Shopify";
            case "LOCAL" -> "Local";
            default -> platform.trim();
        };
    }

    public static String feedback(Integer score, String percentage) {
        if (score == null && (percentage == null || percentage.isBlank())) {
            return "";
        }
        String scoreText = score == null ? "—" : NumberFormat.getIntegerInstance(Locale.US).format(score);
        String pct = percentage == null || percentage.isBlank() ? "—" : percentage.trim();
        if (!"—".equals(pct) && !pct.endsWith("%")) {
            pct = pct + "%";
        }
        return "Feedback " + scoreText + " / " + pct;
    }

    private static String platformBadge(NotificationEmail email, String assetBaseUrl) {
        String src = platformLogoSrc(email.platform(), assetBaseUrl);
        if (src.isBlank()) {
            return "";
        }
        String label = platformLabel(email.platform());
        int[] size = platformLogoSize(email.platform());
        return "<td valign=\"middle\" style=\"padding:0 0 0 10px;\">"
                + "<img src=\"" + esc(src) + "\" alt=\"" + esc(label) + "\" width=\"" + size[0] + "\" height=\""
                + size[1] + "\" style=\"width:" + size[0] + "px;height:" + size[1]
                + "px;border:0;outline:0;text-decoration:none;display:block;\" />"
                + "</td>";
    }

    static String platformLogoSrc(String platform, String assetBaseUrl) {
        String file = platformLogoFile(platform);
        if (file == null) {
            return "";
        }
        String path = "/email/" + file;
        if (assetBaseUrl == null || assetBaseUrl.isBlank()) {
            return path;
        }
        return assetBaseUrl.replaceAll("/+$", "") + path;
    }

    private static String platformLogoFile(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return switch (platform.trim().toUpperCase(Locale.ROOT)) {
            case "EBAY" -> "ebay-logo.png";
            case "BRICKLINK" -> "bricklink-logo.png";
            default -> null;
        };
    }

    /** Display size matching the type-tag height (24px). */
    private static int[] platformLogoSize(String platform) {
        if (platform != null && platform.trim().equalsIgnoreCase("BRICKLINK")) {
            return new int[] {108, 24};
        }
        return new int[] {60, 24};
    }

    private static String sellerHtml(NotificationEmail email) {
        if (!email.buyingOpportunity()) {
            return "";
        }
        String name = email.sellerName() == null ? "" : email.sellerName().trim();
        String meta = email.sellerMeta() == null ? "" : email.sellerMeta().trim();
        if (name.isBlank() && meta.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<tr><td style=\"padding:0 28px 16px;font-family:'Source Sans 3',Arial,sans-serif;font-size:14px;color:")
                .append(SLATE).append(";\">");
        if (!name.isBlank()) {
            html.append("Seller <span style=\"color:").append(INK).append(";font-weight:600;\">")
                    .append(esc(name)).append("</span>");
        }
        if (!meta.isBlank()) {
            if (!name.isBlank()) {
                html.append("<br />");
            }
            html.append(esc(meta));
        }
        html.append("</td></tr>");
        return html.toString();
    }

    private static String heading(NotificationEmail email) {
        String setNumber = email.setNumber() == null ? "" : email.setNumber().trim();
        String setName = email.setName() == null ? "" : email.setName().trim();
        if (!setNumber.isBlank() && !setName.isBlank()) {
            return setNumber + " / " + setName;
        }
        return !setNumber.isBlank() ? setNumber : setName;
    }

    private static String button(String label, String href, String background, String color, boolean outlined) {
        String border = outlined ? "1px solid " + LINE : "0";
        return "<a href=\"" + esc(href) + "\" style=\"display:inline-block;margin:0 10px 8px 0;padding:10px 18px;"
                + "border-radius:999px;text-decoration:none;font-family:'Source Sans 3',Arial,sans-serif;"
                + "font-size:14px;font-weight:600;background:" + background + ";color:" + color
                + ";border:" + border + ";\">" + esc(label) + "</a>";
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
