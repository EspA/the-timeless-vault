package com.thetimelessvault.common;

import com.thetimelessvault.catalog.CatalogItem;

public final class DefaultListingCopy {

    private DefaultListingCopy() {
    }

    public static String description(CatalogItem catalog) {
        String setNumber = catalog.getSetNumber() == null ? "" : catalog.getSetNumber();
        String released = catalog.getReleasedDate() == null ? "—" : String.valueOf(catalog.getReleasedDate().getYear());
        String retired = catalog.getRetiredDate() == null
                ? (Boolean.TRUE.equals(catalog.getRetired()) ? "Yes" : "—")
                : String.valueOf(catalog.getRetiredDate().getYear());
        String pieces = catalog.getPiecesCount() == null ? "—" : String.valueOf(catalog.getPiecesCount());
        String minifigs = catalog.getMinifigsCount() == null || catalog.getMinifigsCount() <= 0
                ? ""
                : "<p><strong>Minifigs: </strong>" + catalog.getMinifigsCount() + "</p>";
        return String.join("",
                "<p><strong>Set number:</strong> <span>" + setNumber + "</span></p>",
                "<p><strong>Condition:</strong><span> </span><b>New Sealed In Box (NISB)<span> </span></b>-<span> </span>Factory seals intact. Never opened.</p>",
                "<p><strong>Box Grade:<span> 10<b> (Collector Grade):</b></span></strong> Gift-ready or investment-grade. Sharp corners, original seal tension, and minimal to no shelf wear.</p>",
                "<p><strong>Released: </strong>" + released + "</p>",
                "<p><strong>Retired:</strong> " + retired + "</p>",
                "<p><strong>Pieces: </strong>" + pieces + "</p>",
                minifigs,
                "<p><span>For more details about our grading system, </span><a href=\"https://www.thetimelessvault.shop/pages/grading\">click here</a><span>.</span></p>",
                "<p><span><strong>Condition Disclaimer:</strong> This is a retired, second-hand set. Please review all high-resolution photos carefully. By purchasing, you acknowledge the specific box/seal condition as shown.</span></p>"
        );
    }

    public static String shortDescription(CatalogItem catalog) {
        return DescriptionHtml.shortDescriptionFromListingHtml(description(catalog));
    }
}
