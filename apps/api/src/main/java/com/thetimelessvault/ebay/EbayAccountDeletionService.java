package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.market.MarketListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class EbayAccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(EbayAccountDeletionService.class);

    static final String CONNECTED_USER_ID_KEY = "ebay.connected_user_id";
    static final String CONNECTED_USERNAME_KEY = "ebay.connected_username";

    private final AppSettingRepository settings;
    private final MarketListingRepository marketListings;
    private final EbayTokenService tokens;
    private final ObjectMapper mapper;
    private final RestClient restClient = RestClient.builder().build();

    public EbayAccountDeletionService(
            AppSettingRepository settings,
            MarketListingRepository marketListings,
            EbayTokenService tokens,
            ObjectMapper mapper
    ) {
        this.settings = settings;
        this.marketListings = marketListings;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    void storeConnectedIdentity(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            String raw = restClient.get()
                    .uri("https://apiz.ebay.com/commerce/identity/v1/user/")
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode user = mapper.readTree(raw == null ? "{}" : raw);
            String userId = user.path("userId").asText(null);
            String username = user.path("username").asText(null);
            if (userId != null && !userId.isBlank()) {
                settings.save(new AppSetting(CONNECTED_USER_ID_KEY, userId));
            }
            if (username != null && !username.isBlank()) {
                settings.save(new AppSetting(CONNECTED_USERNAME_KEY, username));
            }
            log.info("Stored eBay connected identity for account deletion compliance");
        } catch (Exception e) {
            log.warn("Could not store eBay connected identity: {}", e.getMessage());
        }
    }

    @Transactional
    public void process(EbayAccountDeletionNotification notification) {
        if (notification == null || notification.notification() == null) {
            log.warn("Ignoring empty eBay account deletion notification");
            return;
        }
        var payload = notification.notification();
        var data = payload.data();
        String notificationId = payload.notificationId();
        String userId = data == null ? null : data.userId();
        String username = data == null ? null : data.username();

        log.info(
                "Processing eBay account deletion notification {} for userId={} username={}",
                notificationId,
                userId,
                username
        );

        if (username != null && !username.isBlank()) {
            long removed = marketListings.deleteByPlatformAndSellerIgnoreCase(Platform.EBAY, username);
            if (removed > 0) {
                log.info("Removed {} market listing rows for deleted eBay seller {}", removed, username);
            }
        }

        if (matchesConnectedAccount(userId, username)) {
            tokens.clearStoredCredentials();
            log.info("Cleared stored eBay OAuth credentials for deleted connected account");
        }
    }

    private boolean matchesConnectedAccount(String userId, String username) {
        String storedUserId = settings.findById(CONNECTED_USER_ID_KEY)
                .map(AppSetting::getValue)
                .orElse(null);
        if (storedUserId != null && userId != null && storedUserId.equals(userId)) {
            return true;
        }
        String storedUsername = settings.findById(CONNECTED_USERNAME_KEY)
                .map(AppSetting::getValue)
                .orElse(null);
        return storedUsername != null
                && username != null
                && storedUsername.equalsIgnoreCase(username);
    }
}
