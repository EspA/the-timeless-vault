package com.thetimelessvault.inbound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingCarrierTest {

    @Test
    void mapsChannelProviderNamesWithoutTreatingUspsAsUps() {
        assertEquals(ShippingCarrier.USPS, ShippingCarrier.fromProvider("USPS"));
        assertEquals(ShippingCarrier.USPS, ShippingCarrier.fromProvider("USPS Priority Mail"));
        assertEquals(ShippingCarrier.UPS, ShippingCarrier.fromProvider("UPS"));
        assertEquals(ShippingCarrier.UPS, ShippingCarrier.fromProvider("UPS Ground"));
        assertEquals(ShippingCarrier.FEDEX, ShippingCarrier.fromProvider("FedEx"));
        assertEquals(ShippingCarrier.FEDEX, ShippingCarrier.fromProvider("FedEx Home Delivery"));
        assertNull(ShippingCarrier.fromProvider("DHL"));
        assertNull(ShippingCarrier.fromProvider(""));
    }

    @Test
    void infersUpsFromOneZTrackingWhenProviderIsMissing() {
        assertEquals(ShippingCarrier.UPS, ShippingCarrier.fromTrackingNumber("1Z999AA10123456784"));
        assertEquals(ShippingCarrier.UPS, ShippingCarrier.resolve(null, "1zabc"));
        assertEquals(ShippingCarrier.USPS, ShippingCarrier.resolve("USPS", "1Z999"));
        assertNull(ShippingCarrier.fromTrackingNumber("9400111899223197428490"));
    }

    @Test
    void onlyUpsUspsAndFedexAreTrackable() {
        assertTrue(ShippingCarrier.UPS.trackable());
        assertTrue(ShippingCarrier.USPS.trackable());
        assertTrue(ShippingCarrier.FEDEX.trackable());
        assertFalse(ShippingCarrier.DHL.trackable());
    }
}
