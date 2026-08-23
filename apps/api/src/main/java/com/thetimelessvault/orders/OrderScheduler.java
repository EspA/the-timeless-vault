package com.thetimelessvault.orders;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.sales", name = "local-schedule", havingValue = "true")
public class OrderScheduler {

    private final OrderSyncService orderSyncService;

    public OrderScheduler(OrderSyncService orderSyncService) {
        this.orderSyncService = orderSyncService;
    }

    @Scheduled(
            initialDelayString = "${app.sales.sync-delay-ms:300000}",
            fixedDelayString = "${app.sales.sync-delay-ms:300000}"
    )
    public void sync() {
        orderSyncService.sync();
    }
}
