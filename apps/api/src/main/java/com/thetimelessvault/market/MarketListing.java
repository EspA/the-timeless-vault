package com.thetimelessvault.market;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_listing")
public class MarketListing {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id")
    private MarketSnapshot snapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id")
    private CatalogItem catalogItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String fingerprint;

    @Column(columnDefinition = "text")
    private String title;

    private BigDecimal price;
    private Integer quantity;
    private String condition;
    private String seller;

    @Column(name = "seller_country")
    private String sellerCountry;

    private String url;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "seller_feedback_score")
    private Integer sellerFeedbackScore;

    @Column(name = "seller_feedback_percentage")
    private String sellerFeedbackPercentage;

    @Column(nullable = false)
    private boolean auction;

    @Column(name = "current_bid")
    private BigDecimal currentBid;

    @Column(name = "best_offer", nullable = false)
    private boolean bestOffer;

    @Column(name = "shipping_cost")
    private BigDecimal shippingCost;

    @Column(name = "shipping_calculated", nullable = false)
    private boolean shippingCalculated;

    @Column(name = "is_own", nullable = false)
    private boolean own;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static MarketListing create(MarketSnapshot snapshot, CatalogItem catalog, Platform platform) {
        MarketListing listing = new MarketListing();
        listing.id = UUID.randomUUID();
        listing.snapshot = snapshot;
        listing.catalogItem = catalog;
        listing.platform = platform;
        listing.createdAt = Instant.now();
        return listing;
    }

    public UUID getId() {
        return id;
    }

    public MarketSnapshot getSnapshot() {
        return snapshot;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getSeller() {
        return seller;
    }

    public void setSeller(String seller) {
        this.seller = seller;
    }

    public String getSellerCountry() {
        return sellerCountry;
    }

    public void setSellerCountry(String sellerCountry) {
        this.sellerCountry = sellerCountry;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getSellerFeedbackScore() {
        return sellerFeedbackScore;
    }

    public void setSellerFeedbackScore(Integer sellerFeedbackScore) {
        this.sellerFeedbackScore = sellerFeedbackScore;
    }

    public String getSellerFeedbackPercentage() {
        return sellerFeedbackPercentage;
    }

    public void setSellerFeedbackPercentage(String sellerFeedbackPercentage) {
        this.sellerFeedbackPercentage = sellerFeedbackPercentage;
    }

    public boolean isAuction() {
        return auction;
    }

    public void setAuction(boolean auction) {
        this.auction = auction;
    }

    public BigDecimal getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(BigDecimal currentBid) {
        this.currentBid = currentBid;
    }

    public boolean isBestOffer() {
        return bestOffer;
    }

    public void setBestOffer(boolean bestOffer) {
        this.bestOffer = bestOffer;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public boolean isShippingCalculated() {
        return shippingCalculated;
    }

    public void setShippingCalculated(boolean shippingCalculated) {
        this.shippingCalculated = shippingCalculated;
    }

    public boolean isOwn() {
        return own;
    }

    public void setOwn(boolean own) {
        this.own = own;
    }
}
