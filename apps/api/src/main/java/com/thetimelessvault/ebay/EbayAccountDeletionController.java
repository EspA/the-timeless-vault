package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/ebay/account-deletion")
public class EbayAccountDeletionController {

    private static final Logger log = LoggerFactory.getLogger(EbayAccountDeletionController.class);

    private final AppProperties.Ebay config;
    private final EbayAccountDeletionService deletionService;
    private final EbayNotificationSignatureVerifier signatureVerifier;
    private final ObjectMapper mapper;

    public EbayAccountDeletionController(
            AppProperties properties,
            EbayAccountDeletionService deletionService,
            EbayNotificationSignatureVerifier signatureVerifier,
            ObjectMapper mapper
    ) {
        this.config = properties.getEbay();
        this.deletionService = deletionService;
        this.signatureVerifier = signatureVerifier;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<?> verify(@RequestParam(name = "challenge_code", required = false) String challengeCode) {
        if (!configured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "eBay account deletion webhook is not configured"));
        }
        if (challengeCode == null || challengeCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "challenge_code is required"));
        }
        String response = EbayAccountDeletionChallenge.response(
                challengeCode,
                config.getVerificationToken(),
                config.getAccountDeletionEndpointUrl()
        );
        return ResponseEntity.ok(Map.of("challengeResponse", response));
    }

    @PostMapping
    public ResponseEntity<Void> notify(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-EBAY-SIGNATURE", required = false) String signatureHeader
    ) {
        if (!configured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!signatureVerifier.verify(rawBody, signatureHeader)) {
            log.warn("Rejected eBay account deletion notification with invalid signature");
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }
        try {
            EbayAccountDeletionNotification notification = mapper.readValue(rawBody, EbayAccountDeletionNotification.class);
            deletionService.process(notification);
        } catch (Exception e) {
            log.error("Failed to parse eBay account deletion notification", e);
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    private boolean configured() {
        return config.accountDeletionConfigured();
    }
}
