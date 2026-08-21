package com.thetimelessvault.ebay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class EbayAccountDeletionChallenge {

    private EbayAccountDeletionChallenge() {
    }

    static String response(String challengeCode, String verificationToken, String endpointUrl) {
        if (challengeCode == null || challengeCode.isBlank()) {
            throw new IllegalArgumentException("challenge_code is required");
        }
        if (verificationToken == null || verificationToken.isBlank()) {
            throw new IllegalArgumentException("verification token is required");
        }
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("endpoint URL is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(challengeCode.getBytes(StandardCharsets.UTF_8));
            digest.update(verificationToken.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(endpointUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
