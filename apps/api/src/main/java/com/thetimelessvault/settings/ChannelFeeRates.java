package com.thetimelessvault.settings;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ChannelFeeRates {

    public static final String BRICKLINK_PERCENT_KEY = "sales.bricklink.fee_percent";
    public static final String SHOPIFY_PERCENT_KEY = "sales.shopify.fee_percent";
    public static final String BRICKOWL_PERCENT_KEY = "sales.brickowl.fee_percent";
    public static final BigDecimal DEFAULT_BRICKLINK_PERCENT = new BigDecimal("5.4");
    public static final BigDecimal DEFAULT_SHOPIFY_PERCENT = new BigDecimal("2.9");
    public static final BigDecimal DEFAULT_BRICKOWL_PERCENT = new BigDecimal("5.65");

    public record Rates(BigDecimal bricklinkPercent, BigDecimal shopifyPercent, BigDecimal brickowlPercent) {
    }

    private final AppSettingRepository settings;

    public ChannelFeeRates(AppSettingRepository settings) {
        this.settings = settings;
    }

    public BigDecimal bricklinkPercent() {
        return read(BRICKLINK_PERCENT_KEY, DEFAULT_BRICKLINK_PERCENT);
    }

    public BigDecimal shopifyPercent() {
        return read(SHOPIFY_PERCENT_KEY, DEFAULT_SHOPIFY_PERCENT);
    }

    public BigDecimal brickowlPercent() {
        return read(BRICKOWL_PERCENT_KEY, DEFAULT_BRICKOWL_PERCENT);
    }

    public Rates rates() {
        return new Rates(bricklinkPercent(), shopifyPercent(), brickowlPercent());
    }

    public BigDecimal feeFor(Platform platform, BigDecimal unitPrice, int quantity, BigDecimal shippingCost) {
        BigDecimal percent = percentFor(platform);
        if (percent == null) {
            return null;
        }
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        BigDecimal shipping = shippingCost == null ? BigDecimal.ZERO : shippingCost;
        BigDecimal rate = percent.movePointLeft(2);
        BigDecimal merchandise = price.multiply(BigDecimal.valueOf(Math.max(1, quantity)));
        BigDecimal taxable = platform == Platform.BRICKOWL ? merchandise : merchandise.add(shipping);
        return taxable.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Rates save(BigDecimal bricklinkPercent, BigDecimal shopifyPercent, BigDecimal brickowlPercent) {
        BigDecimal bricklink = normalize(bricklinkPercent, "BrickLink fee percent");
        BigDecimal shopify = normalize(shopifyPercent, "Shopify fee percent");
        BigDecimal brickowl = normalize(brickowlPercent, "Brick Owl fee percent");
        settings.save(new AppSetting(BRICKLINK_PERCENT_KEY, bricklink.toPlainString()));
        settings.save(new AppSetting(SHOPIFY_PERCENT_KEY, shopify.toPlainString()));
        settings.save(new AppSetting(BRICKOWL_PERCENT_KEY, brickowl.toPlainString()));
        return new Rates(bricklink, shopify, brickowl);
    }

    private BigDecimal percentFor(Platform platform) {
        if (platform == Platform.BRICKLINK) {
            return bricklinkPercent();
        }
        if (platform == Platform.SHOPIFY) {
            return shopifyPercent();
        }
        if (platform == Platform.BRICKOWL) {
            return brickowlPercent();
        }
        return null;
    }

    private BigDecimal read(String key, BigDecimal fallback) {
        return settings.findById(key)
                .map(AppSetting::getValue)
                .map(value -> {
                    try {
                        return normalize(new BigDecimal(value.trim()), "Fee percent");
                    } catch (RuntimeException e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private static BigDecimal normalize(BigDecimal value, String label) {
        if (value == null) {
            throw ApiException.badRequest(label + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest(label + " cannot be negative");
        }
        if (value.compareTo(new BigDecimal("100")) > 0) {
            throw ApiException.badRequest(label + " is too large");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }
}
