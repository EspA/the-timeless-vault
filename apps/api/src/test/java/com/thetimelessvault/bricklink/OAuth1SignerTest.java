package com.thetimelessvault.bricklink;

import org.junit.jupiter.api.Test;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OAuth1SignerTest {

    @Test
    void includesQueryParametersInTheSignature() {
        String withQuery = OAuth1Signer.authorizationHeader(
                "GET",
                "https://api.bricklink.com/api/store/v1/orders?direction=in&filed=false",
                "ck", "cs", "tk", "ts",
                "nonce", "1700000000"
        );
        String withoutQuery = OAuth1Signer.authorizationHeader(
                "GET",
                "https://api.bricklink.com/api/store/v1/orders",
                "ck", "cs", "tk", "ts",
                "nonce", "1700000000"
        );

        assertNotEquals(signature(withQuery), signature(withoutQuery));
        assertFalse(withQuery.contains("direction"));
        assertFalse(withQuery.contains("filed"));
    }

    @Test
    void parsesQueryParametersIntoTheSigningMap() {
        SortedMap<String, String> params = new TreeMap<>();
        OAuth1Signer.addQueryParams(
                "https://api.bricklink.com/api/store/v1/orders?direction=in&filed=false",
                params
        );
        assertEquals("in", params.get("direction"));
        assertEquals("false", params.get("filed"));
    }

    private static String signature(String header) {
        Matcher matcher = Pattern.compile("oauth_signature=\"([^\"]+)\"").matcher(header);
        return matcher.find() ? matcher.group(1) : header;
    }
}
