package com.thetimelessvault.settings;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbayBrowseProviderSettingsTest {

    @Mock
    AppSettingRepository settings;

    AppProperties properties;
    EbayBrowseProviderSettings providers;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        providers = new EbayBrowseProviderSettings(settings, properties);
    }

    @Test
    void defaultsToWaitSeeBuyWhenPartnerTokenIsSet() {
        properties.getEbay().getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");
        when(settings.findById(EbayBrowseProviderSettings.KEY)).thenReturn(Optional.empty());

        assertTrue(providers.usePartner());
        assertEquals(EbayBrowseProviderSettings.WAITSEEBUY, providers.activeProvider());
        assertEquals("https://waitseebuy.com", providers.browseApiHost());
        assertTrue(providers.browseConfigured());
    }

    @Test
    void settingEbayUsesOfficialHostWhilePartnerTokenRemains() {
        properties.getEbay().getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");
        properties.getEbay().setClientId("sell-id");
        properties.getEbay().setClientSecret("sell-secret");
        when(settings.findById(EbayBrowseProviderSettings.KEY))
                .thenReturn(Optional.of(new AppSetting(EbayBrowseProviderSettings.KEY, "ebay")));

        assertFalse(providers.usePartner());
        assertEquals(EbayBrowseProviderSettings.EBAY, providers.activeProvider());
        assertEquals("https://api.ebay.com", providers.browseApiHost());
        assertTrue(providers.browseConfigured());
    }

    @Test
    void forcingEbayWithoutClientCredentialsIsNotBrowseReady() {
        properties.getEbay().getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");
        when(settings.findById(EbayBrowseProviderSettings.KEY))
                .thenReturn(Optional.of(new AppSetting(EbayBrowseProviderSettings.KEY, "ebay")));

        assertFalse(providers.usePartner());
        assertFalse(providers.browseConfigured());
    }

    @Test
    void withoutPartnerTokenEbayIsTheOnlySource() {
        properties.getEbay().setBrowseClientId("browse-id");
        properties.getEbay().setBrowseClientSecret("browse-secret");

        assertFalse(providers.usePartner());
        assertEquals("https://api.ebay.com", providers.browseApiHost());
        assertFalse(providers.partnerAvailable());
    }

    @Test
    void saveWaitSeeBuyPersistsChoice() {
        properties.getEbay().getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");

        assertEquals("waitseebuy", providers.save(" WaitSeeBuy "));
        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(settings).save(captor.capture());
        assertEquals(EbayBrowseProviderSettings.KEY, captor.getValue().getKey());
        assertEquals("waitseebuy", captor.getValue().getValue());
    }

    @Test
    void saveWaitSeeBuyWithoutTokenFails() {
        ApiException error = assertThrows(ApiException.class, () -> providers.save("waitseebuy"));
        assertEquals(
                "WAITSEEBUY_BROWSE_TOKEN is not set. eBay Browse is the only available source.",
                error.getMessage()
        );
    }

    @Test
    void rejectsUnknownProvider() {
        ApiException error = assertThrows(ApiException.class, () -> providers.save("bricklink"));
        assertEquals("Browse source must be waitseebuy or ebay.", error.getMessage());
    }
}
