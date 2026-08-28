package com.thetimelessvault.settings;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelFeeRatesTest {

    @Mock AppSettingRepository settings;

    @InjectMocks
    ChannelFeeRates rates;

    @Test
    void usesDefaultPercentsWhenMissing() {
        when(settings.findById(ChannelFeeRates.BRICKLINK_PERCENT_KEY)).thenReturn(Optional.empty());
        when(settings.findById(ChannelFeeRates.SHOPIFY_PERCENT_KEY)).thenReturn(Optional.empty());
        when(settings.findById(ChannelFeeRates.BRICKOWL_PERCENT_KEY)).thenReturn(Optional.empty());

        assertEquals(0, new BigDecimal("5.400").compareTo(rates.bricklinkPercent()));
        assertEquals(0, new BigDecimal("2.900").compareTo(rates.shopifyPercent()));
        assertEquals(0, new BigDecimal("5.650").compareTo(rates.brickowlPercent()));
    }

    @Test
    void brickLinkFeeIsPricePlusShippingTimesRate() {
        when(settings.findById(ChannelFeeRates.BRICKLINK_PERCENT_KEY)).thenReturn(Optional.empty());

        assertEquals(
                0,
                new BigDecimal("45.09").compareTo(rates.feeFor(
                        Platform.BRICKLINK, new BigDecimal("820.00"), 1, new BigDecimal("15.00")))
        );
    }

    @Test
    void shopifyFeeUsesConfiguredPercent() {
        when(settings.findById(ChannelFeeRates.SHOPIFY_PERCENT_KEY))
                .thenReturn(Optional.of(new AppSetting(ChannelFeeRates.SHOPIFY_PERCENT_KEY, "3.1")));

        assertEquals(
                0,
                new BigDecimal("28.52").compareTo(rates.feeFor(
                        Platform.SHOPIFY, new BigDecimal("910.00"), 1, new BigDecimal("10.00")))
        );
    }

    @Test
    void brickOwlFeeIsMerchandiseOnly() {
        when(settings.findById(ChannelFeeRates.BRICKOWL_PERCENT_KEY)).thenReturn(Optional.empty());

        assertEquals(
                0,
                new BigDecimal("46.33").compareTo(rates.feeFor(
                        Platform.BRICKOWL, new BigDecimal("820.00"), 1, new BigDecimal("15.00")))
        );
    }

    @Test
    void ebayHasNoConfiguredFee() {
        assertNull(rates.feeFor(Platform.EBAY, new BigDecimal("100"), 1, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativePercent() {
        assertThrows(ApiException.class, () -> rates.save(new BigDecimal("-1"), new BigDecimal("2.9"), new BigDecimal("2.65")));
    }
}
