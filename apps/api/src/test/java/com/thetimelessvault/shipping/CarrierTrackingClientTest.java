package com.thetimelessvault.shipping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void upsPrefersRescheduledDateOverScheduledAndSkipsDeliveredDate() throws Exception {
        var body = mapper.readTree("""
                {
                  "trackResponse": {
                    "shipment": [{
                      "package": [{
                        "currentStatus": { "code": "021", "description": "In Transit" },
                        "deliveryDate": [
                          { "type": "SDD", "date": "20260828" },
                          { "type": "RDD", "date": "20260829" },
                          { "type": "DEL", "date": "20260827" }
                        ]
                      }]
                    }]
                  }
                }
                """);
        var snapshot = CarrierTrackingClient.upsSnapshot(body);
        assertFalse(snapshot.delivered());
        assertEquals(LocalDate.of(2026, 8, 29), snapshot.expectedArrival());
    }

    @Test
    void upsUsesActualDeliveryDateOnceDelivered() throws Exception {
        var body = mapper.readTree("""
                {
                  "trackResponse": {
                    "shipment": [{
                      "package": [{
                        "currentStatus": { "code": "011", "description": "Delivered" },
                        "deliveryDate": [
                          { "type": "SDD", "date": "20260828" },
                          { "type": "DEL", "date": "20260827" }
                        ]
                      }]
                    }]
                  }
                }
                """);
        var snapshot = CarrierTrackingClient.upsSnapshot(body);
        assertTrue(snapshot.delivered());
        assertEquals(LocalDate.of(2026, 8, 27), snapshot.expectedArrival());
    }

    @Test
    void uspsUsesExpectedDeliveryDateWhileInTransit() throws Exception {
        var snapshot = CarrierTrackingClient.uspsSnapshot(mapper.readTree("""
                {
                  "statusCategory": "In Transit",
                  "expectedDeliveryDate": "2026-08-30",
                  "deliveryDate": "2026-08-22"
                }
                """));
        assertFalse(snapshot.delivered());
        assertEquals(LocalDate.of(2026, 8, 30), snapshot.expectedArrival());
    }

    @Test
    void fedexUsesEstimatedDeliveryWindow() throws Exception {
        var snapshot = CarrierTrackingClient.fedexSnapshot(mapper.readTree("""
                {
                  "output": {
                    "completeTrackResults": [{
                      "trackResults": [{
                        "latestStatusDetail": { "code": "IT", "statusByLocale": "In transit" },
                        "estimatedDeliveryTimeWindow": {
                          "window": { "begins": "2026-08-28T08:00:00", "ends": "2026-08-28T20:00:00" }
                        }
                      }]
                    }]
                  }
                }
                """));
        assertFalse(snapshot.delivered());
        assertEquals(LocalDate.of(2026, 8, 28), snapshot.expectedArrival());
    }

    @Test
    void parseDateAcceptsUpsAndIsoFormats() {
        assertEquals(LocalDate.of(2026, 8, 28), CarrierTrackingClient.parseDate("20260828"));
        assertEquals(LocalDate.of(2026, 8, 28), CarrierTrackingClient.parseDate("2026-08-28"));
        assertEquals(LocalDate.of(2026, 8, 28), CarrierTrackingClient.parseDate("2026-08-28T08:00:00"));
    }
}
