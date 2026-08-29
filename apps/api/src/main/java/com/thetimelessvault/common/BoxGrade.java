package com.thetimelessvault.common;

public enum BoxGrade {
    GRADE_10(10, "Collector Grade", "Gift-ready or investment-grade. Sharp corners, original seal tension, the box is in mint condition."),
    GRADE_9(9, "Collector Grade", "Gift-ready or investment-grade. Sharp corners, original seal tension, and minimal to no shelf wear."),
    GRADE_8(8, "Excellent", "Minor shelf wear, small corner blunting, or a clean price sticker. Perfect for display."),
    GRADE_7(7, "Very good", "Minor shelf wear, small corner blunting, or a clean price sticker. Perfect for display."),
    GRADE_6(6, "Good", "Noticeable creasing, small punctures, or minor \"shelf-push\" (dents)."),
    GRADE_5(5, "Fair", "Noticeable creasing, small punctures, or minor \"shelf-push\" (dents)."),
    GRADE_4(4, "Poor/Damaged", "Significant crushing, heavy tape, or structural tears. Recommended for builders who don't keep the box.");

    private final int score;
    private final String band;
    private final String description;

    BoxGrade(int score, String band, String description) {
        this.score = score;
        this.band = band;
        this.description = description;
    }

    public int score() {
        return score;
    }

    public String band() {
        return band;
    }

    public String description() {
        return description;
    }

    public static BoxGrade of(int score) {
        for (BoxGrade grade : values()) {
            if (grade.score == score) {
                return grade;
            }
        }
        return GRADE_10;
    }
}
