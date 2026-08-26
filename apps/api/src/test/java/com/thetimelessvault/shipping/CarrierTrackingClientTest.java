package com.thetimelessvault.shipping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierTrackingClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void upsDeliveredUsesPackageCurrentStatus() throws Exception {
        var body = mapper.readTree("""
                {
                  "trackResponse": {
                    "shipment": [{
                      "package": [{
                        "currentStatus": { "code": "011", "description": "Delivered" }
                      }]
                    }]
                  }
                }
                """);
        assertTrue(CarrierTrackingClient.upsLooksDelivered(body));
    }

    @Test
    void upsInTransitIsNotDelivered() throws Exception {
        var body = mapper.readTree("""
                {
                  "trackResponse": {
                    "shipment": [{
                      "package": [{
                        "currentStatus": { "code": "021", "description": "In Transit" }
                      }]
                    }]
                  }
                }
                """);
        assertFalse(CarrierTrackingClient.upsLooksDelivered(body));
    }

    @Test
    void uspsDeliveredUsesStatusCategory() throws Exception {
        var body = mapper.readTree("""
                { "statusCategory": "Delivered", "status": "Delivered, Front Door/Porch" }
                """);
        assertTrue(CarrierTrackingClient.uspsLooksDelivered(body));
        assertFalse(CarrierTrackingClient.uspsLooksDelivered(mapper.readTree("""
                { "statusCategory": "In Transit", "status": "Arrived at USPS Regional Facility" }
                """)));
    }

    @Test
    void fedexDeliveredUsesLatestStatusDetail() throws Exception {
        var body = mapper.readTree("""
                {
                  "output": {
                    "completeTrackResults": [{
                      "trackResults": [{
                        "latestStatusDetail": { "code": "DL", "statusByLocale": "Delivered" }
                      }]
                    }]
                  }
                }
                """);
        assertTrue(CarrierTrackingClient.fedexLooksDelivered(body));
        assertFalse(CarrierTrackingClient.fedexLooksDelivered(mapper.readTree("""
                {
                  "output": {
                    "completeTrackResults": [{
                      "trackResults": [{
                        "latestStatusDetail": { "code": "IT", "statusByLocale": "In transit" }
                      }]
                    }]
                  }
                }
                """)));
    }
}
