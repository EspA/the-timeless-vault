package com.thetimelessvault.settings;

import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.brickowl.BrickOwlClient;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.ebay.EbayBrowseContext;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.ebay.EbaySellDefaults;
import com.thetimelessvault.ebay.EbayTokenService;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.shopify.ShopifyClient;
import com.thetimelessvault.storage.ObjectStorage;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SettingsController {

    private final AppProperties properties;
    private final ShopifyClient shopifyClient;
    private final BrickLinkClient brickLinkClient;
    private final BrickOwlClient brickOwlClient;
    private final EbayClient ebayClient;
    private final EbayTokenService ebayTokens;
    private final AppSettingRepository appSettings;
    private final ObjectStorage storage;
    private final WatchDefaults watchDefaults;
    private final NotificationMailer notificationMailer;
    private final PriceGuardDefaults priceGuardDefaults;
    private final ChannelFeeRates channelFeeRates;
    private final EbayStoreCategorySettings storeCategorySettings;
    private final ApiCallStatsService apiCallStats;
    private final EbayBrowseProviderSettings browseProviderSettings;

    public SettingsController(
            AppProperties properties,
            ShopifyClient shopifyClient,
            BrickLinkClient brickLinkClient,
            BrickOwlClient brickOwlClient,
            EbayClient ebayClient,
            EbayTokenService ebayTokens,
            AppSettingRepository appSettings,
            ObjectStorage storage,
            WatchDefaults watchDefaults,
            NotificationMailer notificationMailer,
            PriceGuardDefaults priceGuardDefaults,
            ChannelFeeRates channelFeeRates,
            EbayStoreCategorySettings storeCategorySettings,
            ApiCallStatsService apiCallStats,
            EbayBrowseProviderSettings browseProviderSettings
    ) {
        this.properties = properties;
        this.shopifyClient = shopifyClient;
        this.brickLinkClient = brickLinkClient;
        this.brickOwlClient = brickOwlClient;
        this.ebayClient = ebayClient;
        this.ebayTokens = ebayTokens;
        this.appSettings = appSettings;
        this.storage = storage;
        this.watchDefaults = watchDefaults;
        this.notificationMailer = notificationMailer;
        this.priceGuardDefaults = priceGuardDefaults;
        this.channelFeeRates = channelFeeRates;
        this.storeCategorySettings = storeCategorySettings;
        this.apiCallStats = apiCallStats;
        this.browseProviderSettings = browseProviderSettings;
    }

    @GetMapping("/settings/health")
    public Map<String, Object> health() {
        String ebayLocation = "";
        String ebaySellError = "";
        boolean ebayPoliciesReady = false;
        if (ebayClient.sellReady()) {
            try {
                EbaySellDefaults defaults = ebayClient.sellDefaults();
                ebayLocation = defaults.merchantLocationKey();
                ebayPoliciesReady = true;
            } catch (Exception e) {
                ebaySellError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        Map<String, Object> health = new java.util.LinkedHashMap<>();
        health.put("brickeconomy", properties.getBrickeconomy().configured());
        health.put("shopify", shopifyClient.configured());
        health.put("bricklink", brickLinkClient.configured());
        health.put("brickowl", brickOwlClient.healthy());
        health.put("ebay", ebayClient.configured());
        health.put("ebayBrowseReady", ebayClient.browseConfigured());
        health.put("ebayBrowseProvider", ebayClient.browseProvider());
        health.put("ebayBrowseHost", ebayClient.browseApiHost());
        health.put("waitseebuyBrowseConfigured", ebayClient.waitseebuyBrowseConfigured());
        health.put("ebaySellReady", ebayClient.sellReady());
        health.put("ebayOAuth", ebayTokens.hasRefreshToken());
        health.put("ebayLocation", ebayLocation);
        health.put("ebayPoliciesReady", ebayPoliciesReady);
        health.put("ebaySellError", ebaySellError);
        health.put("storage", storage.mode());
        health.putAll(notificationMailer.status());
        health.put("ebayStoreCategories", storeCategorySettings.list());
        health.put("ebayBuyerPostalCode", ebayClient.buyerPostalCode());
        health.put("ebayDefaultExcludeWords", watchDefaults.excludeWords());
        PriceGuardDefaults.Thresholds thresholds = priceGuardDefaults.thresholds();
        health.put("priceGuardHighPercent", thresholds.highPercent());
        health.put("priceGuardLowPercent", thresholds.lowPercent());
        ChannelFeeRates.Rates fees = channelFeeRates.rates();
        health.put("bricklinkFeePercent", fees.bricklinkPercent());
        health.put("shopifyFeePercent", fees.shopifyPercent());
        health.put("brickowlFeePercent", fees.brickowlPercent());
        return health;
    }

    @GetMapping("/settings/statistics")
    public ApiCallStatsService.Snapshot statistics() {
        return apiCallStats.snapshot();
    }

    @PutMapping({"/settings/notification-email", "/settings/alert-email"})
    public Map<String, Object> saveNotificationEmail(@RequestBody(required = false) Map<String, String> body) {
        String email = notificationMailer.saveRecipient(body == null ? "" : body.get("email"));
        return Map.of("email", email, "mailDeliversToInbox", notificationMailer.deliversToInbox());
    }

    @PostMapping({"/settings/notification-email/test", "/settings/alert-email/test"})
    public Map<String, String> sendTestNotificationEmail() {
        notificationMailer.sendTest();
        return Map.of("status", "sent", "email", notificationMailer.recipient());
    }

    @PutMapping("/settings/ebay-browse-provider")
    public Map<String, Object> saveEbayBrowseProvider(@RequestBody(required = false) Map<String, String> body) {
        String provider = browseProviderSettings.save(body == null ? "" : body.get("provider"));
        return Map.of(
                "provider", provider,
                "ebayBrowseProvider", ebayClient.browseProvider(),
                "ebayBrowseHost", ebayClient.browseApiHost(),
                "ebayBrowseReady", ebayClient.browseConfigured(),
                "waitseebuyBrowseConfigured", ebayClient.waitseebuyBrowseConfigured()
        );
    }

    @PutMapping("/settings/ebay-buyer-postal-code")
    public Map<String, String> saveEbayBuyerPostalCode(@RequestBody(required = false) Map<String, String> body) {
        String zip = EbayBrowseContext.normalizePostalCode(body == null ? "" : body.get("postalCode"));
        appSettings.save(new AppSetting(EbayBrowseContext.BUYER_POSTAL_CODE_KEY, zip));
        return Map.of("postalCode", zip);
    }

    @PutMapping("/settings/ebay-default-exclude-words")
    public Map<String, String> saveEbayDefaultExcludeWords(@RequestBody(required = false) Map<String, String> body) {
        String words = watchDefaults.saveExcludeWords(body == null ? "" : body.get("excludeWords"));
        return Map.of("excludeWords", words);
    }

    @PutMapping("/settings/price-guard-thresholds")
    public Map<String, Object> savePriceGuardThresholds(@RequestBody(required = false) Map<String, Object> body) {
        PriceGuardDefaults.Thresholds saved = priceGuardDefaults.save(
                decimal(body == null ? null : body.get("highPercent")),
                decimal(body == null ? null : body.get("lowPercent"))
        );
        return Map.of("highPercent", saved.highPercent(), "lowPercent", saved.lowPercent());
    }

    @PutMapping("/settings/channel-fee-rates")
    public Map<String, Object> saveChannelFeeRates(@RequestBody(required = false) Map<String, Object> body) {
        ChannelFeeRates.Rates saved = channelFeeRates.save(
                decimal(body == null ? null : body.get("bricklinkPercent")),
                decimal(body == null ? null : body.get("shopifyPercent")),
                decimal(body == null ? null : body.get("brickowlPercent"))
        );
        return Map.of(
                "bricklinkPercent", saved.bricklinkPercent(),
                "shopifyPercent", saved.shopifyPercent(),
                "brickowlPercent", saved.brickowlPercent()
        );
    }

    @GetMapping("/shopify/collections")
    public List<Map<String, String>> collections() {
        if (!shopifyClient.configured()) {
            return List.of();
        }
        return shopifyClient.collections();
    }

    @GetMapping("/ebay/store-categories")
    public List<Map<String, String>> ebayStoreCategories() {
        return storeCategorySettings.entries();
    }

    @GetMapping("/settings/ebay-store-categories")
    public Map<String, Object> listEbayStoreCategories() {
        return Map.of("categories", storeCategorySettings.list());
    }

    @PostMapping("/settings/ebay-store-categories")
    public Map<String, Object> addEbayStoreCategory(@RequestBody(required = false) Map<String, String> body) {
        return Map.of("categories", storeCategorySettings.add(body == null ? "" : body.get("name")));
    }

    @DeleteMapping("/settings/ebay-store-categories")
    public Map<String, Object> deleteEbayStoreCategory(@RequestParam String name) {
        return Map.of("categories", storeCategorySettings.delete(name));
    }

    @GetMapping("/ebay/oauth/start")
    public Map<String, String> ebayStart() {
        String state = UUID.randomUUID().toString();
        return Map.of("url", ebayTokens.authorizationUrl(state));
    }

    @GetMapping("/ebay/oauth/callback")
    public RedirectView ebayCallback(@RequestParam String code) {
        ebayTokens.exchangeAuthorizationCode(code);
        return new RedirectView("/settings?ebay=connected");
    }

    @PostMapping("/ebay/oauth/complete")
    public Map<String, Object> ebayComplete(@RequestBody Map<String, String> body) {
        ebayTokens.exchangeAuthorizationCode(body == null ? "" : body.getOrDefault("code", ""));
        return Map.of("ok", true, "ebayOAuth", true);
    }

    @GetMapping("/ebay/location/defaults")
    public Map<String, String> ebayLocationDefaults() {
        return shopifyClient.shipFromAddress();
    }

    @PostMapping("/ebay/location")
    public Map<String, Object> ebayLocation(@RequestBody(required = false) Map<String, String> body) {
        Map<String, String> address = shopifyClient.shipFromAddress();
        if (body != null) {
            body.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    address.put(key, value.trim());
                }
            });
        }
        String key = ebayClient.ensureWarehouseLocation(address);
        EbaySellDefaults defaults = ebayClient.sellDefaults();
        return Map.of(
                "ok", true,
                "merchantLocationKey", key,
                "ebayLocation", defaults.merchantLocationKey(),
                "ebayPoliciesReady", true
        );
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : new BigDecimal(text);
    }
}
