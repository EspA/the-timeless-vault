package com.thetimelessvault.common;

import com.thetimelessvault.catalog.CatalogItem;

public final class DefaultListingCopy {

    private DefaultListingCopy() {
    }

    public static String description(CatalogItem catalog) {
        return description(catalog, ItemCondition.NEW_SEALED, BoxGrade.GRADE_10);
    }

    public static String description(CatalogItem catalog, ItemCondition condition, BoxGrade boxGrade) {
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
                conditionParagraph(condition),
                boxGradeParagraph(boxGrade),
                "<p><strong>Released: </strong>" + released + "</p>",
                "<p><strong>Retired:</strong> " + retired + "</p>",
                "<p><strong>Pieces: </strong>" + pieces + "</p>",
                minifigs,
                "<p><span>For more details about our grading system, </span><a href=\"https://www.thetimelessvault.shop/pages/grading\">click here</a><span>.</span></p>",
                "<p><span><strong>Condition Disclaimer:</strong> This is a retired, second-hand set. Please review all high-resolution photos carefully. By purchasing, you acknowledge the specific box/seal condition as shown.</span></p>"
        );
    }

    public static String conditionParagraph(ItemCondition condition) {
        ItemCondition value = condition == null ? ItemCondition.NEW_SEALED : condition;
        return switch (value) {
            case NEW_COMPLETE -> "<p><strong>Condition:</strong><span> </span><b>New Open Box (NOB)<span> </span></b>-<span> </span>All bags sealed with instructions.</p>";
            case NEW_INCOMPLETE -> "<p><strong>Condition:</strong><span> </span><b>New Open Box (NOB)<span> </span></b>-<span> </span>Some bags or instructions missing</p>";
            case NEW_OTHER -> "<p><strong>Condition:</strong><span> </span>new other</p>";
            case USED_COMPLETE -> "<p><strong>Used 100% Complete:</strong> Previously built. Verified against official part lists to include all bricks, minifigures, and instructions.</p>";
            case USED_INCOMPLETE -> "<p><strong>Used Missing Parts:</strong> Previously built. Known missing pieces will be listed in the item description.</p>";
            case NEW_SEALED -> "<p><strong>Condition:</strong><span> </span><b>New Sealed In Box (NISB)<span> </span></b>-<span> </span>Factory seals intact. Never opened.</p>";
        };
    }

    public static String boxGradeParagraph(BoxGrade boxGrade) {
        BoxGrade grade = boxGrade == null ? BoxGrade.GRADE_10 : boxGrade;
        return "<p><strong>Box Grade:</strong> " + grade.score() + "/10 (" + grade.band() + "): " + grade.description() + "</p>";
    }

    public static String shortDescription(CatalogItem catalog) {
        return DescriptionHtml.shortDescriptionFromListingHtml(description(catalog));
    }
}
