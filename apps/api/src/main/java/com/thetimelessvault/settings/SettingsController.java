package com.thetimelessvault.settings;

import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.ebay.EbayBrowseContext;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.ebay.EbaySellDefaults;
import com.thetimelessvault.ebay.EbayTokenService;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.shopify.ShopifyClient;
import com.thetimelessvault.storage.ObjectStorage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

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

    public SettingsController(
            AppProperties properties,
            ShopifyClient shopifyClient,
            BrickLinkClient brickLinkClient,
            EbayClient ebayClient,
            EbayTokenService ebayTokens,
            AppSettingRepository appSettings,
            ObjectStorage storage,
            WatchDefaults watchDefaults
    ) {
        this.properties = properties;
        this.shopifyClient = shopifyClient;
        this.brickLinkClient = brickLinkClient;
        this.ebayClient = ebayClient;
        this.ebayTokens = ebayTokens;
        this.appSettings = appSettings;
        this.storage = storage;
        this.watchDefaults = watchDefaults;
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
        return Map.ofEntries(
                Map.entry("brickeconomy", properties.getBrickeconomy().configured()),
                Map.entry("shopify", shopifyClient.configured()),
                Map.entry("bricklink", brickLinkClient.configured()),
                Map.entry("ebay", ebayClient.configured()),
                Map.entry("ebayBrowseReady", ebayClient.browseConfigured()),
                Map.entry("ebaySellReady", ebayClient.sellReady()),
                Map.entry("ebayOAuth", ebayTokens.hasRefreshToken()),
                Map.entry("ebayLocation", ebayLocation),
                Map.entry("ebayPoliciesReady", ebayPoliciesReady),
                Map.entry("ebaySellError", ebaySellError),
                Map.entry("storage", storage.mode()),
                Map.entry("mailFrom", properties.getMailFrom()),
                Map.entry("alertTo", properties.getAlertToEmail() == null ? "" : properties.getAlertToEmail()),
                Map.entry("ebayStoreCategories", ThemeMapper.EBAY_STORE_CATEGORIES),
                Map.entry("ebayBuyerPostalCode", ebayClient.buyerPostalCode()),
                Map.entry("ebayDefaultExcludeWords", watchDefaults.excludeWords())
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

    @GetMapping("/shopify/collections")
    public List<Map<String, String>> collections() {
        if (!shopifyClient.configured()) {
            return List.of();
        }
        return shopifyClient.collections();
    }

    @GetMapping("/ebay/store-categories")
    public List<Map<String, String>> ebayStoreCategories() {
        return ebayClient.storeCategories();
    }

    @GetMapping("/ebay/oauth/start")
    public Map<String, String> ebayStart() {
        String state = UUID.randomUUID().toString();
        return Map.of("url", ebayTokens.authorizationUrl(state));
    }

    @GetMapping("/ebay/oauth/callback")
    public RedirectView ebayCallback(@RequestParam String code) {
        ebayTokens.exchangeAuthorizationCode(code);
        return new RedirectView(properties.getFrontendOrigin() + "/settings?ebay=connected");
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
}
