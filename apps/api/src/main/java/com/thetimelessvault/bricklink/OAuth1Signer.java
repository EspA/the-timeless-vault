package com.thetimelessvault.bricklink;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
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
        return authorizationHeader(
                method,
                url,
                consumerKey,
                consumerSecret,
                token,
                tokenSecret,
                Long.toHexString(new SecureRandom().nextLong()),
                Long.toString(Instant.now().getEpochSecond())
        );
    }

    static String authorizationHeader(
            String method,
            String url,
            String consumerKey,
            String consumerSecret,
            String token,
            String tokenSecret,
            String nonce,
            String timestamp
    ) {
        SortedMap<String, String> oauth = new TreeMap<>();
        oauth.put("oauth_consumer_key", consumerKey);
        oauth.put("oauth_nonce", nonce);
        oauth.put("oauth_signature_method", "HMAC-SHA1");
        oauth.put("oauth_timestamp", timestamp);
        oauth.put("oauth_token", token);
        oauth.put("oauth_version", "1.0");

        SortedMap<String, String> signing = new TreeMap<>(oauth);
        addQueryParams(url, signing);

        String baseString = method.toUpperCase() + "&" + encode(normalizeUrl(url)) + "&" + encode(join(signing));
        String signingKey = encode(consumerSecret) + "&" + encode(tokenSecret);
        oauth.put("oauth_signature", hmacSha1(baseString, signingKey));

        return "OAuth " + oauth.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=\"" + encode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));
    }

    static void addQueryParams(String url, SortedMap<String, String> params) {
        if (url == null) {
            return;
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return;
        }
        String query = url.substring(queryStart + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) {
            query = query.substring(0, hash);
        }
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            if (!key.isBlank()) {
                params.put(key, value);
            }
        }
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

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
