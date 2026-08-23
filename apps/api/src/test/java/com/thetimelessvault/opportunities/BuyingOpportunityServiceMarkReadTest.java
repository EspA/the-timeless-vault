package com.thetimelessvault.opportunities;

import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.inventory.PhotoRepository;
import com.thetimelessvault.orders.OrderRepository;
import com.thetimelessvault.settings.NotificationMailer;
import com.thetimelessvault.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyingOpportunityServiceMarkReadTest {

    @Mock BuyingOpportunityRepository opportunities;
    @Mock OrderRepository orders;
    @Mock NotificationMailer notificationMailer;
    @Mock PhotoRepository photos;
    @Mock ObjectStorage storage;

    BuyingOpportunityService service;

    @BeforeEach
    void setUp() {
        service = new BuyingOpportunityService(
                opportunities,
                orders,
                notificationMailer,
                new AppProperties(),
                photos,
                storage
        );
    }

    @Test
    void markUnreadClearsReadAt() {
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_NEW_SALE,
                "New sale",
                "SALE:TEST"
        );
        opportunity.setReadAt(Instant.parse("2026-08-23T12:00:00Z"));
        when(opportunities.findById(opportunity.getId())).thenReturn(Optional.of(opportunity));
        when(opportunities.save(any(BuyingOpportunity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BuyingOpportunity updated = service.markUnread(opportunity.getId());

        assertNull(updated.getReadAt());
        verify(opportunities).save(opportunity);
    }

    @Test
    void markReadSetsReadAt() {
        BuyingOpportunity opportunity = BuyingOpportunity.create(
                BuyingOpportunity.TYPE_NEW_SALE,
                "New sale",
                "SALE:TEST"
        );
        when(opportunities.findById(opportunity.getId())).thenReturn(Optional.of(opportunity));
        when(opportunities.save(any(BuyingOpportunity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BuyingOpportunity updated = service.markRead(opportunity.getId());

        assertNotNull(updated.getReadAt());
    }
}
