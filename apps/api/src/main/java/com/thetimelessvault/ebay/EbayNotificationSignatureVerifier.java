package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EbayNotificationSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(EbayNotificationSignatureVerifier.class);
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "-----BEGIN PUBLIC KEY-----(.*?)-----END PUBLIC KEY-----",
            Pattern.DOTALL
    );

    private final AppProperties.Ebay config;
    private final EbayTokenService tokens;
    private final ObjectMapper mapper;
    private final RestClient restClient = RestClient.builder().build();
    private final Map<String, CachedPublicKey> publicKeys = new ConcurrentHashMap<>();

    public EbayNotificationSignatureVerifier(
            AppProperties properties,
            EbayTokenService tokens,
            ObjectMapper mapper
    ) {
        this.config = properties.getEbay();
        this.tokens = tokens;
        this.mapper = mapper;
    }

    boolean verify(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        if (rawBody == null || rawBody.isBlank()) {
            return false;
        }
        try {
            SignatureHeader header = parseSignatureHeader(signatureHeader);
            CachedPublicKey publicKey = fetchPublicKey(header.kid());
            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.rawKey())));
            Signature signature = Signature.getInstance(publicKey.algorithm());
            signature.initVerify(key);
            byte[] payload = canonicalPayload(rawBody);
            signature.update(payload);
            return signature.verify(Base64.getDecoder().decode(header.signature()));
        } catch (Exception e) {
            log.warn("eBay notification signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private byte[] canonicalPayload(String rawBody) throws Exception {
        return mapper.writeValueAsBytes(mapper.readTree(rawBody));
    }

    private SignatureHeader parseSignatureHeader(String signatureHeader) throws Exception {
        JsonNode node = mapper.readTree(Base64.getDecoder().decode(signatureHeader));
        String kid = node.path("kid").asText(null);
        String sig = node.path("signature").asText(null);
        if (kid == null || kid.isBlank() || sig == null || sig.isBlank()) {
            throw new IllegalArgumentException("signature header missing kid or signature");
        }
        return new SignatureHeader(kid, sig);
    }

    private CachedPublicKey fetchPublicKey(String keyId) throws Exception {
        CachedPublicKey cached = publicKeys.get(keyId);
        if (cached != null && !cached.expired()) {
            return cached;
        }
        String accessToken = tokens.appAccessToken();
        String raw = restClient.get()
                .uri(config.apiHost() + "/commerce/notification/v1/public_key/" + keyId)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class);
        JsonNode node = mapper.readTree(raw == null ? "{}" : raw);
        String key = node.path("key").asText(null);
        String digest = node.path("digest").asText("SHA256");
        String algorithm = node.path("algorithm").asText("ECDSA");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("eBay public key response missing key");
        }
        CachedPublicKey resolved = new CachedPublicKey(
                extractRawKey(key),
                String.format("%swith%s", digest, algorithm),
                System.currentTimeMillis() + 3_600_000L
        );
        publicKeys.put(keyId, resolved);
        return resolved;
    }

    private static String extractRawKey(String key) {
        Matcher matcher = KEY_PATTERN.matcher(key);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s", "");
        }
        return key.replaceAll("\\s", "");
    }

    private record SignatureHeader(String kid, String signature) {
    }

    private record CachedPublicKey(String rawKey, String algorithm, long expiresAtMs) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }
}
