package com.thetimelessvault.publish;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingLogService {

    private final ListingLogRepository logs;

    public ListingLogService(ListingLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            InventoryItem item,
            Platform platform,
            ListingAction action,
            ListingLogStatus status,
            String message
    ) {
        logs.save(ListingLog.create(item, platform, action, status, message));
    }

    public Page<ListingLog> list(int page, int size) {
        int pageSize = Math.min(10_000, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        return logs.findAllByOrderByLoggedAtDesc(PageRequest.of(pageIndex, pageSize));
    }
}
