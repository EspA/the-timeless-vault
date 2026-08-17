package com.thetimelessvault.bricklink;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class OAuth1Signer {

    private OAuth1Signer() {
    }

    public static String authorizationHeader(
            String method,
            String url,
            String consumerKey,
            String consumerSecret,
            String token,
            String tokenSecret
    ) {
        String nonce = Long.toHexString(new SecureRandom().nextLong());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        SortedMap<String, String> params = new TreeMap<>();
        params.put("oauth_consumer_key", consumerKey);
        params.put("oauth_nonce", nonce);
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", timestamp);
        params.put("oauth_token", token);
        params.put("oauth_version", "1.0");

        String baseString = method.toUpperCase() + "&" + encode(normalizeUrl(url)) + "&" + encode(join(params));
        String signingKey = encode(consumerSecret) + "&" + encode(tokenSecret);
        String signature = hmacSha1(baseString, signingKey);
        params.put("oauth_signature", signature);

        return "OAuth " + params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=\"" + encode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));
    }

    private static String normalizeUrl(String url) {
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }

    private static String join(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String hmacSha1(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign BrickLink request", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
