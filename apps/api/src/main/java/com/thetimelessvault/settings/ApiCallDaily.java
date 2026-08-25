package com.thetimelessvault.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "api_call_daily")
@IdClass(ApiCallDailyId.class)
public class ApiCallDaily {

    @Id
    private LocalDate day;

    @Id
    private String platform;

    @Column(name = "call_count", nullable = false)
    private long callCount;

    public static ApiCallDaily of(LocalDate day, String platform, long callCount) {
        ApiCallDaily row = new ApiCallDaily();
        row.day = day;
        row.platform = platform;
        row.callCount = callCount;
        return row;
    }

    public LocalDate getDay() {
        return day;
    }

    public String getPlatform() {
        return platform;
    }

    public long getCallCount() {
        return callCount;
    }
}
