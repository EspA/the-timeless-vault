package com.thetimelessvault.settings;

import com.thetimelessvault.bricklink.BrickLinkClient;
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
    private final EbayClient ebayClient;
    private final EbayTokenService ebayTokens;
    private final AppSettingRepository appSettings;
    private final ObjectStorage storage;
    private final WatchDefaults watchDefaults;
    private final AlertMailer alertMailer;
    private final PriceGuardDefaults priceGuardDefaults;
    private final EbayStoreCategorySettings storeCategorySettings;

    public SettingsController(
            AppProperties properties,
            ShopifyClient shopifyClient,
            BrickLinkClient brickLinkClient,
            EbayClient ebayClient,
            EbayTokenService ebayTokens,
            AppSettingRepository appSettings,
            ObjectStorage storage,
            WatchDefaults watchDefaults,
            AlertMailer alertMailer,
            PriceGuardDefaults priceGuardDefaults,
            EbayStoreCategorySettings storeCategorySettings
    ) {
        this.properties = properties;
        this.shopifyClient = shopifyClient;
        this.brickLinkClient = brickLinkClient;
        this.ebayClient = ebayClient;
        this.ebayTokens = ebayTokens;
        this.appSettings = appSettings;
        this.storage = storage;
        this.watchDefaults = watchDefaults;
        this.alertMailer = alertMailer;
        this.priceGuardDefaults = priceGuardDefaults;
        this.storeCategorySettings = storeCategorySettings;
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
        health.put("ebay", ebayClient.configured());
        health.put("ebayBrowseReady", ebayClient.browseConfigured());
        health.put("ebaySellReady", ebayClient.sellReady());
        health.put("ebayOAuth", ebayTokens.hasRefreshToken());
        health.put("ebayLocation", ebayLocation);
        health.put("ebayPoliciesReady", ebayPoliciesReady);
        health.put("ebaySellError", ebaySellError);
        health.put("storage", storage.mode());
        health.putAll(alertMailer.status());
        health.put("ebayStoreCategories", storeCategorySettings.list());
        health.put("ebayBuyerPostalCode", ebayClient.buyerPostalCode());
        health.put("ebayDefaultExcludeWords", watchDefaults.excludeWords());
        PriceGuardDefaults.Thresholds thresholds = priceGuardDefaults.thresholds();
        health.put("priceGuardHighPercent", thresholds.highPercent());
        health.put("priceGuardLowPercent", thresholds.lowPercent());
        return health;
    }

    @PutMapping("/settings/alert-email")
    public Map<String, Object> saveAlertEmail(@RequestBody(required = false) Map<String, String> body) {
        String email = alertMailer.saveRecipient(body == null ? "" : body.get("email"));
        return Map.of("email", email, "mailDeliversToInbox", alertMailer.deliversToInbox());
    }

    @PostMapping("/settings/alert-email/test")
    public Map<String, String> sendTestAlertEmail() {
        alertMailer.sendTest();
        return Map.of("status", "sent", "email", alertMailer.recipient());
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
