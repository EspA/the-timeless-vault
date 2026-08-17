package com.thetimelessvault.settings;

import com.thetimelessvault.ebay.EbayMarketFilters;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class WatchDefaults {

    public static final String EXCLUDE_WORDS_KEY = "ebay.default_exclude_words";

    private final AppSettingRepository settings;

    public WatchDefaults(AppSettingRepository settings) {
        this.settings = settings;
    }

    public String excludeWords() {
        return settings.findById(EXCLUDE_WORDS_KEY)
                .map(AppSetting::getValue)
                .orElse(EbayMarketFilters.DEFAULT_EXCLUDE_WORDS);
    }

    public String saveExcludeWords(String words) {
        String value = words == null ? "" : words.trim();
        settings.save(new AppSetting(EXCLUDE_WORDS_KEY, value));
        return value;
    }
}
