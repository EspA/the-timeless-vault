package com.thetimelessvault.settings;

import com.thetimelessvault.alerts.PriceGuard;
import com.thetimelessvault.alerts.PriceGuardRepository;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.publish.ChannelListing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceGuardDefaultsTest {

    @Mock AppSettingRepository settings;
    @Mock PriceGuardRepository priceGuards;

    @InjectMocks
    PriceGuardDefaults defaults;

    @Test
    void usesFifteenWhenSettingIsMissing() {
        when(settings.findById(PriceGuardDefaults.HIGH_PERCENT_KEY)).thenReturn(Optional.empty());
        when(settings.findById(PriceGuardDefaults.LOW_PERCENT_KEY)).thenReturn(Optional.empty());
        assertEquals(new BigDecimal("15"), defaults.highPercent());
        assertEquals(new BigDecimal("15"), defaults.lowPercent());
    }

    @Test
    void usesSavedPercents() {
        when(settings.findById(PriceGuardDefaults.HIGH_PERCENT_KEY))
                .thenReturn(Optional.of(new AppSetting(PriceGuardDefaults.HIGH_PERCENT_KEY, "20")));
        when(settings.findById(PriceGuardDefaults.LOW_PERCENT_KEY))
                .thenReturn(Optional.of(new AppSetting(PriceGuardDefaults.LOW_PERCENT_KEY, "10.5")));
        assertEquals(0, new BigDecimal("20.00").compareTo(defaults.highPercent()));
        assertEquals(0, new BigDecimal("10.50").compareTo(defaults.lowPercent()));
    }

    @Test
    void saveUpdatesSettingsAndExistingGuards() {
        PriceGuard guard = PriceGuard.create(new ChannelListing());
        when(priceGuards.findAll()).thenReturn(List.of(guard));

        PriceGuardDefaults.Thresholds saved = defaults.save(new BigDecimal("25"), new BigDecimal("8"));

        assertEquals(0, new BigDecimal("25").compareTo(saved.highPercent()));
        assertEquals(0, new BigDecimal("8").compareTo(saved.lowPercent()));
        assertEquals(0, new BigDecimal("25").compareTo(guard.getHighPercent()));
        assertEquals(0, new BigDecimal("8").compareTo(guard.getLowPercent()));
        verify(settings, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
        verify(priceGuards).saveAll(List.of(guard));
    }

    @Test
    void rejectsNegativePercent() {
        assertThrows(ApiException.class, () -> defaults.save(new BigDecimal("-1"), new BigDecimal("15")));
    }
}
