package com.thetimelessvault.settings;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class EbayBrowseProviderSettings {

    public static final String KEY = "ebay.browse_provider";
    public static final String EBAY = "ebay";
    public static final String WAITSEEBUY = "waitseebuy";

    private final AppSettingRepository settings;
    private final AppProperties.Ebay config;

    public EbayBrowseProviderSettings(AppSettingRepository settings, AppProperties properties) {
        this.settings = settings;
        this.config = properties.getEbay();
    }

    public String storedProvider() {
        return settings.findById(KEY)
                .map(AppSetting::getValue)
                .map(String::trim)
                .orElse("");
    }

    public boolean usePartner() {
        if (!config.partnerBrowseConfigured()) {
            return false;
        }
        String stored = storedProvider();
        return stored.isBlank() || WAITSEEBUY.equalsIgnoreCase(stored);
    }

    public String activeProvider() {
        return usePartner() ? WAITSEEBUY : EBAY;
    }

    public String browseApiHost() {
        return usePartner() ? config.partnerBrowseHost() : config.ebayBrowseApiHost();
    }

    public boolean browseConfigured() {
        return usePartner() || config.ebayBrowseCredentialsConfigured();
    }

    public boolean partnerAvailable() {
        return config.partnerBrowseConfigured();
    }

    public String save(String raw) {
        String provider = normalize(raw);
        if (WAITSEEBUY.equals(provider) && !config.partnerBrowseConfigured()) {
            throw ApiException.badRequest(
                    "WAITSEEBUY_BROWSE_TOKEN is not set. eBay Browse is the only available source."
            );
        }
        settings.save(new AppSetting(KEY, provider));
        return provider;
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Choose waitseebuy or ebay.");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (EBAY.equals(value) || WAITSEEBUY.equals(value)) {
            return value;
        }
        throw ApiException.badRequest("Browse source must be waitseebuy or ebay.");
    }
}
