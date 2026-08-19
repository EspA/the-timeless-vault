package com.thetimelessvault.settings;

import com.thetimelessvault.alerts.PriceGuard;
import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PriceGuardDefaults {

    public static final String HIGH_PERCENT_KEY = "price_guard.high_percent";
    public static final String LOW_PERCENT_KEY = "price_guard.low_percent";
    public static final BigDecimal DEFAULT_PERCENT = new BigDecimal("15");

    public record Thresholds(BigDecimal highPercent, BigDecimal lowPercent) {
    }

    private final AppSettingRepository settings;
    private final PriceGuardRepository priceGuards;

    public PriceGuardDefaults(AppSettingRepository settings, PriceGuardRepository priceGuards) {
        this.settings = settings;
        this.priceGuards = priceGuards;
    }

    public BigDecimal highPercent() {
        return read(HIGH_PERCENT_KEY);
    }

    public BigDecimal lowPercent() {
        return read(LOW_PERCENT_KEY);
    }

    public Thresholds thresholds() {
        return new Thresholds(highPercent(), lowPercent());
    }

    @Transactional
    public Thresholds save(BigDecimal highPercent, BigDecimal lowPercent) {
        BigDecimal high = normalize(highPercent, "High percent");
        BigDecimal low = normalize(lowPercent, "Low percent");
        settings.save(new AppSetting(HIGH_PERCENT_KEY, high.toPlainString()));
        settings.save(new AppSetting(LOW_PERCENT_KEY, low.toPlainString()));
        List<PriceGuard> guards = priceGuards.findAll();
        for (PriceGuard guard : guards) {
            guard.setHighPercent(high);
            guard.setLowPercent(low);
            guard.touch();
        }
        if (!guards.isEmpty()) {
            priceGuards.saveAll(guards);
        }
        return new Thresholds(high, low);
    }

    private BigDecimal read(String key) {
        return settings.findById(key)
                .map(AppSetting::getValue)
                .map(value -> {
                    try {
                        return normalize(new BigDecimal(value.trim()), "Percent");
                    } catch (RuntimeException e) {
                        return DEFAULT_PERCENT;
                    }
                })
                .orElse(DEFAULT_PERCENT);
    }

    private static BigDecimal normalize(BigDecimal value, String label) {
        if (value == null) {
            throw ApiException.badRequest(label + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest(label + " cannot be negative");
        }
        if (value.compareTo(new BigDecimal("999")) > 0) {
            throw ApiException.badRequest(label + " is too large");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
