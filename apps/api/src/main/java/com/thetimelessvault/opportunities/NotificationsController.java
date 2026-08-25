package com.thetimelessvault.opportunities;

import com.thetimelessvault.common.Platform;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/notifications", "/api/alerts"})
public class NotificationsController {

    private final BuyingOpportunityService opportunities;

    public NotificationsController(BuyingOpportunityService opportunities) {
        this.opportunities = opportunities;
    }

    public record BuyingOpportunityView(
            UUID id,
            String type,
            Platform platform,
            String title,
            String body,
            String url,
            Instant createdAt,
            boolean read,
            boolean emailed
    ) {
        static BuyingOpportunityView from(BuyingOpportunity opportunity, String url) {
            return new BuyingOpportunityView(
                    opportunity.getId(),
                    opportunity.getType(),
                    opportunity.getPlatform(),
                    opportunity.getTitle(),
                    opportunity.getBody(),
                    url,
                    opportunity.getCreatedAt(),
                    opportunity.getReadAt() != null,
                    opportunity.getEmailedAt() != null
            );
        }
    }

    private BuyingOpportunityView view(BuyingOpportunity opportunity) {
        return BuyingOpportunityView.from(opportunity, opportunities.displayUrl(opportunity));
    }

    public record NotificationsPageView(
            List<BuyingOpportunityView> items,
            int page,
            int size,
            long total,
            int totalPages
    ) {
    }

    @GetMapping
    public NotificationsPageView list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var result = opportunities.list(page, size);
        return new NotificationsPageView(
                result.getContent().stream().map(this::view).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", opportunities.unreadCount());
    }

    @PostMapping("/{id}/read")
    public BuyingOpportunityView read(@PathVariable UUID id) {
        return view(opportunities.markRead(id));
    }

    @PostMapping("/{id}/unread")
    public BuyingOpportunityView unread(@PathVariable UUID id) {
        return view(opportunities.markUnread(id));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", opportunities.markAllRead());
    }
}
