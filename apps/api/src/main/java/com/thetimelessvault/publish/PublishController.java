package com.thetimelessvault.publish;

import com.thetimelessvault.ebay.EbayCatalogPreview;
import com.thetimelessvault.common.Platform;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/{id}")
public class PublishController {

    private final PublishService publishService;

    public PublishController(PublishService publishService) {
        this.publishService = publishService;
    }

    public record PublishRequest(Set<Platform> platforms) {
    }

    public record ListingView(
            UUID id,
            Platform platform,
            String status,
            String shopifyStatus,
            String bricklinkStatus,
            String ebayStatus,
            String externalId,
            String liveUrl,
            BigDecimal lastPublishedPrice,
            String bricklinkPhotoUploadUrl,
            String lastError,
            Instant updatedAt
    ) {
        static ListingView from(ChannelListing listing) {
            return new ListingView(
                    listing.getId(),
                    listing.getPlatform(),
                    listing.getStatus().name(),
                    listing.getShopifyStatus(),
                    listing.getBricklinkStatus(),
                    listing.getEbayStatus(),
                    listing.getExternalId(),
                    listing.getLiveUrl(),
                    listing.getLastPublishedPrice(),
                    listing.getBricklinkPhotoUploadUrl(),
                    listing.getLastError(),
                    listing.getUpdatedAt()
            );
        }
    }

    public record StatusRequest(String status) {
    }

    public record JobView(UUID id, Platform platform, String status, String error, Instant createdAt, Instant finishedAt) {
        static JobView from(PublishJob job) {
            return new JobView(job.getId(), job.getPlatform(), job.getStatus().name(), job.getError(), job.getCreatedAt(), job.getFinishedAt());
        }
    }

    @GetMapping("/ebay/catalog")
    public EbayCatalogPreview ebayCatalog(@PathVariable UUID id) {
        return publishService.ebayCatalogPreview(id);
    }

    @PostMapping("/publish")
    public List<JobView> publish(@PathVariable UUID id, @RequestBody(required = false) PublishRequest request) {
        List<PublishJob> jobs = publishService.enqueue(id, request == null ? Set.of() : request.platforms());
        publishService.runJobs(jobs.stream().map(PublishJob::getId).toList());
        return jobs.stream().map(JobView::from).toList();
    }

    @PostMapping("/publish/update")
    public List<JobView> update(@PathVariable UUID id, @RequestBody(required = false) PublishRequest request) {
        List<PublishJob> jobs = publishService.enqueueUpdate(id, request == null ? Set.of() : request.platforms());
        publishService.runJobs(jobs.stream().map(PublishJob::getId).toList());
        return jobs.stream().map(JobView::from).toList();
    }

    @PostMapping("/publish/{platform}/retry")
    public JobView retry(@PathVariable UUID id, @PathVariable Platform platform) {
        List<PublishJob> jobs = publishService.enqueueRetry(id, platform);
        publishService.runJobs(jobs.stream().map(PublishJob::getId).toList());
        return JobView.from(jobs.getFirst());
    }

    @GetMapping("/listings")
    public List<ListingView> listings(@PathVariable UUID id) {
        return publishService.listingsFor(id).stream().map(ListingView::from).toList();
    }

    @PutMapping("/listings/shopify/status")
    public ListingView setShopifyStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return ListingView.from(publishService.setShopifyStatus(id, request == null ? null : request.status()));
    }

    @PutMapping("/listings/bricklink/status")
    public ListingView setBricklinkStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return ListingView.from(publishService.setBricklinkStatus(id, request == null ? null : request.status()));
    }

    @PutMapping("/listings/ebay/status")
    public ListingView setEbayStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return ListingView.from(publishService.setEbayStatus(id, request == null ? null : request.status()));
    }

    @DeleteMapping("/listings/shopify")
    public ResponseEntity<Void> deleteShopifyListing(@PathVariable UUID id) {
        publishService.deleteShopifyListing(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/listings/bricklink")
    public ResponseEntity<Void> deleteBricklinkListing(@PathVariable UUID id) {
        publishService.deleteBricklinkListing(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/listings/ebay")
    public ResponseEntity<Void> deleteEbayListing(@PathVariable UUID id) {
        publishService.deleteEbayListing(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jobs")
    public List<JobView> jobs(@PathVariable UUID id) {
        return publishService.jobsFor(id).stream().map(JobView::from).toList();
    }
}
