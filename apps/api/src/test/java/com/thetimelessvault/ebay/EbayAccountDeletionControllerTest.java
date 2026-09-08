package com.thetimelessvault.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbayAccountDeletionControllerTest {

    @Mock
    private EbayAccountDeletionService deletionService;

    @Mock
    private EbayNotificationSignatureVerifier signatureVerifier;

    private EbayAccountDeletionController controller;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getEbay().setClientId("client-id");
        properties.getEbay().setClientSecret("client-secret");
        properties.getEbay().setVerificationToken("token");
        properties.getEbay().setAccountDeletionEndpointUrl(
                "https://admin.thetimelessvault.com/webhooks/ebay/account-deletion"
        );
        controller = new EbayAccountDeletionController(
                properties,
                deletionService,
                signatureVerifier,
                new ObjectMapper()
        );
    }

    @Test
    void returnsNoContentWhenNotificationIsProcessed() {
        when(signatureVerifier.verify(anyString(), anyString())).thenReturn(true);

        var response = controller.notify(validBody(), "sig");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void returnsBadRequestWhenPayloadIsInvalid() {
        when(signatureVerifier.verify(anyString(), anyString())).thenReturn(true);

        var response = controller.notify("{not-json", "sig");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void returnsServiceUnavailableWhenProcessingCannotOpenATransaction() {
        when(signatureVerifier.verify(anyString(), anyString())).thenReturn(true);
        doThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"))
                .when(deletionService).process(any());

        var response = controller.notify(validBody(), "sig");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    private static String validBody() {
        return """
                {
                  "metadata": { "topic": "MARKETPLACE_ACCOUNT_DELETION", "schemaVersion": "1.0" },
                  "notification": {
                    "notificationId": "n-1",
                    "eventDate": "2026-09-08T19:49:00Z",
                    "publishDate": "2026-09-08T19:49:01Z",
                    "publishAttemptCount": 1,
                    "data": { "username": "seller_one", "userId": "user-1" }
                  }
                }
                """;
    }
}
