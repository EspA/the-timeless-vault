package com.thetimelessvault.inbound;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quote")
public class Quote {

    @Id
    private UUID id;

    @Column(name = "quote_number", nullable = false, unique = true)
    private int quoteNumber;

    @Column(name = "shipping_total", nullable = false)
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, createdAt ASC")
    private List<QuoteLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Quote create(int quoteNumber) {
        Quote quote = new Quote();
        quote.id = UUID.randomUUID();
        quote.quoteNumber = quoteNumber;
        quote.shippingTotal = BigDecimal.ZERO.setScale(2);
        quote.status = QuoteStatus.DRAFT;
        quote.createdAt = Instant.now();
        quote.updatedAt = Instant.now();
        return quote;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void addLine(QuoteLine line) {
        line.setQuote(this);
        lines.add(line);
    }

    public void markConverted(PurchaseOrder order) {
        this.status = QuoteStatus.CONVERTED;
        this.purchaseOrder = order;
        touch();
    }

    public boolean isDraft() {
        return status != null && status.isDraft();
    }

    public String displayNumber() {
        return "Quote " + String.format("%03d", quoteNumber);
    }

    public UUID getId() {
        return id;
    }

    public int getQuoteNumber() {
        return quoteNumber;
    }

    public BigDecimal getShippingTotal() {
        return shippingTotal;
    }

    public void setShippingTotal(BigDecimal shippingTotal) {
        this.shippingTotal = shippingTotal;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public List<QuoteLine> getLines() {
        return lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
