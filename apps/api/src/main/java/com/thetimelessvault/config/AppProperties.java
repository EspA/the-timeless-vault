package com.thetimelessvault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:8080";
    private String frontendOrigin = "http://localhost:5173";
    private String allowedEmails = "";
    private String alertToEmail = "";
    private String mailFrom = "vault@thetimelessvault.com";
    private String internalJobToken = "";
    private final Security security = new Security();
    private final Storage storage = new Storage();
    private final Brickeconomy brickeconomy = new Brickeconomy();
    private final Shopify shopify = new Shopify();
    private final Bricklink bricklink = new Bricklink();
    private final Brickowl brickowl = new Brickowl();
    private final Ebay ebay = new Ebay();
    private final OAuthApi ups = new OAuthApi();
    private final OAuthApi usps = new OAuthApi();
    private final OAuthApi fedex = new OAuthApi();

    public List<String> allowedEmailList() {
        return Arrays.stream(allowedEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .toList();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getFrontendOrigin() {
        return frontendOrigin;
    }

    public void setFrontendOrigin(String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    public String getAllowedEmails() {
        return allowedEmails;
    }

    public void setAllowedEmails(String allowedEmails) {
        this.allowedEmails = allowedEmails;
    }

    public String getAlertToEmail() {
        return alertToEmail;
    }

    public void setAlertToEmail(String alertToEmail) {
        this.alertToEmail = alertToEmail;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public String getInternalJobToken() {
        return internalJobToken;
    }

    public void setInternalJobToken(String internalJobToken) {
        this.internalJobToken = internalJobToken;
    }

    public Security getSecurity() {
        return security;
    }

    public Storage getStorage() {
        return storage;
    }

    public Brickeconomy getBrickeconomy() {
        return brickeconomy;
    }

    public Shopify getShopify() {
        return shopify;
    }

    public Bricklink getBricklink() {
        return bricklink;
    }

    public Brickowl getBrickowl() {
        return brickowl;
    }

    public Ebay getEbay() {
        return ebay;
    }

    public OAuthApi getUps() {
        return ups;
    }

    public OAuthApi getUsps() {
        return usps;
    }

    public OAuthApi getFedex() {
        return fedex;
    }

    public static class Security {
        private boolean devBypass;

        public boolean isDevBypass() {
            return devBypass;
        }

        public void setDevBypass(boolean devBypass) {
            this.devBypass = devBypass;
        }
    }

    public static class Storage {
        private String gcsBucket = "";
        private String gcsProjectId = "";
        private String localDir = "./data/photos";

        public boolean gcsEnabled() {
            return gcsBucket != null && !gcsBucket.isBlank();
        }

        public String getGcsBucket() {
            return gcsBucket;
        }

        public void setGcsBucket(String gcsBucket) {
            this.gcsBucket = gcsBucket;
        }

        public String getGcsProjectId() {
            return gcsProjectId;
        }

        public void setGcsProjectId(String gcsProjectId) {
            this.gcsProjectId = gcsProjectId;
        }

        public String getLocalDir() {
            return localDir;
        }

        public void setLocalDir(String localDir) {
            this.localDir = localDir;
        }
    }

    public static class Brickeconomy {
        private String apiKey = "";
        private String baseUrl = "https://www.brickeconomy.com/api/v1";
        private String userAgent = "TheTimelessVault/1.0";

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Shopify {
        private String shopDomain = "";
        private String clientId = "";
        private String clientSecret = "";
        private String adminToken = "";
        private String apiVersion = "2025-10";
        private String categoryId = "";
        private String locationName = "Private Mail Box";

        public boolean configured() {
            return notBlank(shopDomain) && (notBlank(adminToken) || clientCredentialsConfigured());
        }

        public boolean clientCredentialsConfigured() {
            return notBlank(clientId) && notBlank(clientSecret);
        }

        public String shopHost() {
            String domain = shopDomain == null ? "" : shopDomain.trim();
            if (domain.startsWith("https://")) {
                domain = domain.substring("https://".length());
            } else if (domain.startsWith("http://")) {
                domain = domain.substring("http://".length());
            }
            while (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }
            return domain;
        }

        public String getShopDomain() {
            return shopDomain;
        }

        public void setShopDomain(String shopDomain) {
            this.shopDomain = shopDomain;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAdminToken() {
            return adminToken;
        }

        public void setAdminToken(String adminToken) {
            this.adminToken = adminToken;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(String categoryId) {
            this.categoryId = categoryId;
        }

        public String getLocationName() {
            return locationName;
        }

        public void setLocationName(String locationName) {
            this.locationName = locationName;
        }
    }

    public static class Bricklink {
        private String consumerKey = "";
        private String consumerSecret = "";
        private String token = "";
        private String tokenSecret = "";
        private String baseUrl = "https://api.bricklink.com/api/store/v1";

        public boolean configured() {
            return notBlank(consumerKey) && notBlank(consumerSecret) && notBlank(token) && notBlank(tokenSecret);
        }

        public String getConsumerKey() {
            return consumerKey;
        }

        public void setConsumerKey(String consumerKey) {
            this.consumerKey = consumerKey;
        }

        public String getConsumerSecret() {
            return consumerSecret;
        }

        public void setConsumerSecret(String consumerSecret) {
            this.consumerSecret = consumerSecret;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getTokenSecret() {
            return tokenSecret;
        }

        public void setTokenSecret(String tokenSecret) {
            this.tokenSecret = tokenSecret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Brickowl {
        private String apiKey = "";
        private String baseUrl = "https://api.brickowl.com/v1";
        private String userAgent = "TheTimelessVault/1.0";

        public boolean configured() {
            return notBlank(apiKey);
        }

        public String host() {
            String url = baseUrl == null ? "" : baseUrl.trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Ebay {
        private String clientId = "";
        private String clientSecret = "";
        private String ruName = "";
        private String refreshToken = "";
        private String env = "PRODUCTION";
        private String marketplaceId = "EBAY_US";
        private String categoryId = "19006";
        private String merchantLocationKey = "";
        private String fulfillmentPolicyId = "";
        private String paymentPolicyId = "";
        private String returnPolicyId = "";
        private String browseClientId = "";
        private String browseClientSecret = "";
        private String verificationToken = "";
        private String accountDeletionEndpointUrl = "";
        private final WaitSeeBuy waitseebuy = new WaitSeeBuy();

        public boolean accountDeletionConfigured() {
            return notBlank(verificationToken)
                    && notBlank(accountDeletionEndpointUrl)
                    && configured();
        }

        public boolean configured() {
            return notBlank(clientId) && notBlank(clientSecret);
        }

        public boolean partnerBrowseConfigured() {
            return waitseebuy.tokenConfigured();
        }

        public String partnerBrowseToken() {
            return waitseebuy.getBrowseToken();
        }

        public boolean ebayBrowseCredentialsConfigured() {
            return notBlank(browseClientId()) && notBlank(browseClientSecret());
        }

        public boolean browseConfigured() {
            return partnerBrowseConfigured() || ebayBrowseCredentialsConfigured();
        }

        public String browseClientId() {
            return notBlank(browseClientId) ? browseClientId : clientId;
        }

        public String browseClientSecret() {
            return notBlank(browseClientSecret) ? browseClientSecret : clientSecret;
        }

        public String partnerBrowseHost() {
            return waitseebuy.browseHost();
        }

        public String ebayBrowseApiHost() {
            return "https://api.ebay.com";
        }

        public String browseApiHost() {
            if (partnerBrowseConfigured()) {
                return partnerBrowseHost();
            }
            return ebayBrowseApiHost();
        }

        public boolean sellReady() {
            return configured()
                    && notBlank(merchantLocationKey)
                    && notBlank(fulfillmentPolicyId)
                    && notBlank(paymentPolicyId)
                    && notBlank(returnPolicyId);
        }

        public String apiHost() {
            return "SANDBOX".equalsIgnoreCase(env) ? "https://api.sandbox.ebay.com" : "https://api.ebay.com";
        }

        public String authHost() {
            return "SANDBOX".equalsIgnoreCase(env) ? "https://auth.sandbox.ebay.com" : "https://auth.ebay.com";
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRuName() {
            return ruName;
        }

        public void setRuName(String ruName) {
            this.ruName = ruName;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public String getMarketplaceId() {
            return marketplaceId;
        }

        public void setMarketplaceId(String marketplaceId) {
            this.marketplaceId = marketplaceId;
        }

        public String getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(String categoryId) {
            this.categoryId = categoryId;
        }

        public String getMerchantLocationKey() {
            return merchantLocationKey;
        }

        public void setMerchantLocationKey(String merchantLocationKey) {
            this.merchantLocationKey = merchantLocationKey;
        }

        public String getFulfillmentPolicyId() {
            return fulfillmentPolicyId;
        }

        public void setFulfillmentPolicyId(String fulfillmentPolicyId) {
            this.fulfillmentPolicyId = fulfillmentPolicyId;
        }

        public String getPaymentPolicyId() {
            return paymentPolicyId;
        }

        public void setPaymentPolicyId(String paymentPolicyId) {
            this.paymentPolicyId = paymentPolicyId;
        }

        public String getReturnPolicyId() {
            return returnPolicyId;
        }

        public void setReturnPolicyId(String returnPolicyId) {
            this.returnPolicyId = returnPolicyId;
        }

        public String getBrowseClientId() {
            return browseClientId;
        }

        public void setBrowseClientId(String browseClientId) {
            this.browseClientId = browseClientId;
        }

        public String getBrowseClientSecret() {
            return browseClientSecret;
        }

        public void setBrowseClientSecret(String browseClientSecret) {
            this.browseClientSecret = browseClientSecret;
        }

        public String getVerificationToken() {
            return verificationToken;
        }

        public void setVerificationToken(String verificationToken) {
            this.verificationToken = verificationToken;
        }

        public String getAccountDeletionEndpointUrl() {
            return accountDeletionEndpointUrl;
        }

        public void setAccountDeletionEndpointUrl(String accountDeletionEndpointUrl) {
            this.accountDeletionEndpointUrl = accountDeletionEndpointUrl;
        }

        public WaitSeeBuy getWaitseebuy() {
            return waitseebuy;
        }
    }

    public static class WaitSeeBuy {
        private String browseHost = "https://watchseebuy.com";
        private String browseToken = "";

        public boolean tokenConfigured() {
            return notBlank(browseToken);
        }

        public String browseHost() {
            String url = browseHost == null ? "" : browseHost.trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url.isBlank() ? "https://watchseebuy.com" : url;
        }

        public String getBrowseHost() {
            return browseHost;
        }

        public void setBrowseHost(String browseHost) {
            this.browseHost = browseHost;
        }

        public String getBrowseToken() {
            return browseToken;
        }

        public void setBrowseToken(String browseToken) {
            this.browseToken = browseToken;
        }
    }

    public static class OAuthApi {
        private String clientId = "";
        private String clientSecret = "";
        private String baseUrl = "";

        public boolean configured() {
            return notBlank(clientId) && notBlank(clientSecret);
        }

        public String host() {
            String url = baseUrl == null ? "" : baseUrl.trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
