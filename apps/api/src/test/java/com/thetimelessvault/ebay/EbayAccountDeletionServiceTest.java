package com.thetimelessvault.ebay;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.market.MarketListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbayAccountDeletionServiceTest {

    @Mock
    private AppSettingRepository settings;

    @Mock
    private MarketListingRepository marketListings;

    @Mock
    private EbayTokenService tokens;

    @InjectMocks
    private EbayAccountDeletionService service;

    @Test
    void clearsOAuthWhenDeletedUserMatchesConnectedAccount() {
        when(settings.findById(EbayAccountDeletionService.CONNECTED_USER_ID_KEY))
                .thenReturn(Optional.of(new AppSetting(EbayAccountDeletionService.CONNECTED_USER_ID_KEY, "user-1")));
        when(marketListings.deleteByPlatformAndSellerIgnoreCase(eq(Platform.EBAY), eq("seller_one")))
                .thenReturn(2);

        service.process(new EbayAccountDeletionNotification(
                new EbayAccountDeletionNotification.Metadata(
                        "MARKETPLACE_ACCOUNT_DELETION",
                        "1.0",
                        false
                ),
                new EbayAccountDeletionNotification.Notification(
                        "notification-1",
                        "2026-08-21T12:00:00Z",
                        "2026-08-21T12:00:01Z",
                        1,
                        new EbayAccountDeletionNotification.AccountDeletionData(
                                "seller_one",
                                "user-1",
                                "legacy-token"
                        )
                )
        ));

        verify(marketListings).deleteByPlatformAndSellerIgnoreCase(Platform.EBAY, "seller_one");
        verify(tokens).clearStoredCredentials();
    }

    @Test
    void stillRemovesListingsWhenDeletedUserIsNotTheConnectedAccount() {
        when(settings.findById(EbayAccountDeletionService.CONNECTED_USER_ID_KEY))
                .thenReturn(Optional.empty());
        when(settings.findById(EbayAccountDeletionService.CONNECTED_USERNAME_KEY))
                .thenReturn(Optional.empty());
        when(marketListings.deleteByPlatformAndSellerIgnoreCase(eq(Platform.EBAY), eq("other_seller")))
                .thenReturn(1);

        service.process(new EbayAccountDeletionNotification(
                new EbayAccountDeletionNotification.Metadata(
                        "MARKETPLACE_ACCOUNT_DELETION",
                        "1.0",
                        false
                ),
                new EbayAccountDeletionNotification.Notification(
                        "notification-2",
                        "2026-08-21T12:00:00Z",
                        "2026-08-21T12:00:01Z",
                        1,
                        new EbayAccountDeletionNotification.AccountDeletionData(
                                "other_seller",
                                "user-2",
                                "legacy-token"
                        )
                )
        ));

        verify(marketListings).deleteByPlatformAndSellerIgnoreCase(Platform.EBAY, "other_seller");
        verify(tokens, never()).clearStoredCredentials();
    }
}
