package com.thetimelessvault.settings;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ApiCallDailyId implements Serializable {

    private LocalDate day;
    private String platform;

    public ApiCallDailyId() {
    }

    public ApiCallDailyId(LocalDate day, String platform) {
        this.day = day;
        this.platform = platform;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApiCallDailyId that)) {
            return false;
        }
        return Objects.equals(day, that.day) && Objects.equals(platform, that.platform);
    }

    @Override
    public int hashCode() {
        return Objects.hash(day, platform);
    }
}
