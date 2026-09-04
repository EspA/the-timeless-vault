package com.thetimelessvault.alerts;

import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.publish.PublishJob;
import com.thetimelessvault.publish.PublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/listing-adjustments")
public class ListingAdjustmentController {

    private final ListingAdjustmentService adjustments;
    private final InventoryService inventory;
    private final PublishService publish;

    public ListingAdjustmentController(
            ListingAdjustmentService adjustments,
            InventoryService inventory,
            PublishService publish
    ) {
        this.adjustments = adjustments;
        this.inventory = inventory;
        this.publish = publish;
    }

    @GetMapping
    public List<ListingAdjustmentService.ListingAdjustmentView> list() {
        return adjustments.listActive().stream().map(adjustments::view).toList();
    }

    @PostMapping("/{id}/dismiss")
    public ListingAdjustmentService.ListingAdjustmentView dismiss(@PathVariable UUID id) {
        return adjustments.dismiss(id);
    }

    public record ApplyRequest(BigDecimal price) {
    }

    @PostMapping("/{id}/apply")
    public ListingAdjustmentService.ListingAdjustmentView apply(@PathVariable UUID id, @RequestBody ApplyRequest body) {
        var current = adjustments.requireActive(id);
        inventory.updateChannelPrice(current.inventoryItemId(), current.platform(), body == null ? null : body.price());
        List<PublishJob> jobs = publish.enqueueUpdate(current.inventoryItemId(), Set.of(current.platform()));
        publish.runJobs(jobs.stream().map(PublishJob::getId).toList());
        return adjustments.resolve(id);
    }
}
