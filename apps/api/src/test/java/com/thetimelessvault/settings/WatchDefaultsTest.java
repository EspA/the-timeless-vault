package com.thetimelessvault.settings;

import com.thetimelessvault.ebay.EbayMarketFilters;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchDefaultsTest {

    @Mock
    AppSettingRepository settings;

    @InjectMocks
    WatchDefaults watchDefaults;

    @Test
    void usesBuiltInListWhenSettingIsMissing() {
        when(settings.findById(WatchDefaults.EXCLUDE_WORDS_KEY)).thenReturn(Optional.empty());
        assertEquals(EbayMarketFilters.DEFAULT_EXCLUDE_WORDS, watchDefaults.excludeWords());
    }

    @Test
    void usesSavedListIncludingEmpty() {
        when(settings.findById(WatchDefaults.EXCLUDE_WORDS_KEY))
                .thenReturn(Optional.of(new AppSetting(WatchDefaults.EXCLUDE_WORDS_KEY, "-custom")));
        assertEquals("-custom", watchDefaults.excludeWords());

        when(settings.findById(WatchDefaults.EXCLUDE_WORDS_KEY))
                .thenReturn(Optional.of(new AppSetting(WatchDefaults.EXCLUDE_WORDS_KEY, "")));
        assertEquals("", watchDefaults.excludeWords());
    }

    @Test
    void savesTrimmedList() {
        assertEquals("-moc -kit", watchDefaults.saveExcludeWords(" -moc -kit "));
        verify(settings).save(org.mockito.ArgumentMatchers.argThat(setting ->
                WatchDefaults.EXCLUDE_WORDS_KEY.equals(setting.getKey())
                        && "-moc -kit".equals(setting.getValue())));
    }
}
