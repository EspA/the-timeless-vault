package com.thetimelessvault.opportunities;

import com.thetimelessvault.common.Platform;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class BuyingOpportunitiesController {

    private final BuyingOpportunityService opportunities;

    public BuyingOpportunitiesController(BuyingOpportunityService opportunities) {
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
        static BuyingOpportunityView from(BuyingOpportunity opportunity) {
            return new BuyingOpportunityView(
                    opportunity.getId(),
                    opportunity.getType(),
                    opportunity.getPlatform(),
                    opportunity.getTitle(),
                    opportunity.getBody(),
                    opportunity.getUrl(),
                    opportunity.getCreatedAt(),
                    opportunity.getReadAt() != null,
                    opportunity.getEmailedAt() != null
            );
        }
    }

    @GetMapping
    public List<BuyingOpportunityView> list() {
        return opportunities.list().stream().map(BuyingOpportunityView::from).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", opportunities.unreadCount());
    }

    @PostMapping("/{id}/read")
    public BuyingOpportunityView read(@PathVariable UUID id) {
        return BuyingOpportunityView.from(opportunities.markRead(id));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", opportunities.markAllRead());
    }
}
