package com.thetimelessvault.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApiCallStatsService {

    public static final String EBAY = "EBAY";
    public static final String BRICKLINK = "BRICKLINK";
    public static final String SHOPIFY = "SHOPIFY";
    public static final String BRICKECONOMY = "BRICKECONOMY";
    static final ZoneId ZONE = ZoneId.of("America/New_York");

    private static final Logger log = LoggerFactory.getLogger(ApiCallStatsService.class);

    private final ApiCallDailyRepository counts;
    private final TransactionTemplate requiresNew;

    public ApiCallStatsService(ApiCallDailyRepository counts, PlatformTransactionManager transactions) {
        this.counts = counts;
        this.requiresNew = new TransactionTemplate(transactions);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(String platform) {
        if (platform == null || platform.isBlank()) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(status -> counts.increment(today(), platform));
        } catch (Exception e) {
            log.warn("Could not record {} API call", platform, e);
        }
    }

    public Snapshot snapshot() {
        LocalDate today = today();
        LocalDate start = today.minusDays(13);
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (ApiCallDaily row : counts.findByDayGreaterThanEqualOrderByDayDescPlatformAsc(start)) {
            long[] countsForDay = byDay.computeIfAbsent(row.getDay(), day -> new long[4]);
            int index = switch (row.getPlatform()) {
                case EBAY -> 0;
                case BRICKLINK -> 1;
                case SHOPIFY -> 2;
                case BRICKECONOMY -> 3;
                default -> -1;
            };
            if (index >= 0) {
                countsForDay[index] += row.getCallCount();
            }
        }
        List<DayCounts> recent = new ArrayList<>();
        for (LocalDate day = today; !day.isBefore(start); day = day.minusDays(1)) {
            recent.add(dayCounts(day, byDay.get(day)));
        }
        return new Snapshot(ZONE.getId(), recent.getFirst(), recent);
    }

    LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static DayCounts dayCounts(LocalDate day, long[] counts) {
        long ebay = counts == null ? 0 : counts[0];
        long bricklink = counts == null ? 0 : counts[1];
        long shopify = counts == null ? 0 : counts[2];
        long brickeconomy = counts == null ? 0 : counts[3];
        return new DayCounts(day, ebay, bricklink, shopify, brickeconomy, ebay + bricklink + shopify + brickeconomy);
    }

    public record Snapshot(String timeZone, DayCounts today, List<DayCounts> recent) {
    }

    public record DayCounts(
            LocalDate day,
            long ebay,
            long bricklink,
            long shopify,
            long brickeconomy,
            long total
    ) {
    }
}
