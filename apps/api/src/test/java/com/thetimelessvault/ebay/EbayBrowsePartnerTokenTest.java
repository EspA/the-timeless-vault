package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.settings.EbayBrowseProviderSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EbayBrowsePartnerTokenTest {

    @Test
    void partnerTokenIsReturnedWithoutOauth() {
        AppProperties properties = new AppProperties();
        properties.getEbay().getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");

        EbayTokenService tokens = new EbayTokenService(
                properties,
                mock(AppSettingRepository.class),
                new ObjectMapper(),
                mock(ObjectProvider.class)
        );

        assertEquals("partner-browse-token-32-chars-min", tokens.browseAccessToken());
        assertEquals("https://waitseebuy.com", properties.getEbay().browseApiHost());
    }

    @Test
    void settingsOverrideUsesEbayHostAndSkipsPartnerToken() {
        AppProperties properties = new AppProperties();
        properties.getEbay().getWaitseebuy().setBrowseToken("partner-browse-token-32-chars-min");
        AppSettingRepository settings = mock(AppSettingRepository.class);
        when(settings.findById(EbayBrowseProviderSettings.KEY))
                .thenReturn(Optional.of(new AppSetting(EbayBrowseProviderSettings.KEY, "ebay")));
        EbayBrowseProviderSettings providers = new EbayBrowseProviderSettings(settings, properties);

        EbayTokenService tokens = new EbayTokenService(
                properties,
                settings,
                new ObjectMapper(),
                mock(ObjectProvider.class)
        );
        tokens.setBrowseProvider(providers);

        assertFalse(tokens.usePartnerBrowse());
        assertEquals("https://api.ebay.com", providers.browseApiHost());
        ApiException error = assertThrows(ApiException.class, tokens::browseAccessToken);
        assertEquals(
                "eBay Browse is not configured. Set WAITSEEBUY_BROWSE_TOKEN or eBay Browse client credentials.",
                error.getMessage()
        );
    }

    @Test
    void withoutPartnerOrCredentialsBrowseTokenFailsClosed() {
        EbayTokenService tokens = new EbayTokenService(
                new AppProperties(),
                mock(AppSettingRepository.class),
                new ObjectMapper(),
                mock(ObjectProvider.class)
        );

        ApiException error = assertThrows(ApiException.class, tokens::browseAccessToken);
        assertEquals(
                "eBay Browse is not configured. Set WAITSEEBUY_BROWSE_TOKEN or eBay Browse client credentials.",
                error.getMessage()
        );
    }

    @Test
    void resolveBrowseUriJoinsRelativePathsAndKeepsAbsoluteItemHref() {
        assertEquals(
                URI.create("https://waitseebuy.com/buy/browse/v1/item_summary/search?q=LEGO"),
                EbayClient.resolveBrowseUri(
                        "https://waitseebuy.com",
                        "/buy/browse/v1/item_summary/search?q=LEGO"
                )
        );
        assertEquals(
                URI.create("https://waitseebuy.com/buy/browse/v1/item/v1%7C123%7C0"),
                EbayClient.resolveBrowseUri(
                        "https://waitseebuy.com",
                        "https://waitseebuy.com/buy/browse/v1/item/v1%7C123%7C0"
                )
        );
        assertEquals(
                URI.create("https://api.ebay.com/buy/browse/v1/item_summary/search?q=LEGO"),
                EbayClient.resolveBrowseUri(
                        "https://api.ebay.com",
                        "/buy/browse/v1/item_summary/search?q=LEGO"
                )
        );
    }
}
