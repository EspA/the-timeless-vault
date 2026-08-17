package com.thetimelessvault.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "catalog_item")
public class CatalogItem {

    @Id
    private UUID id;

    @Column(name = "set_number", nullable = false, unique = true)
    private String setNumber;

    @Column(nullable = false)
    private String name;

    private String theme;
    private String subtheme;
    private Integer year;

    @Column(name = "pieces_count")
    private Integer piecesCount;

    @Column(name = "minifigs_count")
    private Integer minifigsCount;

    private String upc;
    private String ean;
    private Boolean retired;

    @Column(name = "retired_date")
    private LocalDate retiredDate;

    @Column(name = "released_date")
    private LocalDate releasedDate;

    @Column(name = "current_value_new")
    private BigDecimal currentValueNew;

    @Column(name = "retail_price_us")
    private BigDecimal retailPriceUs;

    @Column(name = "current_value_used")
    private BigDecimal currentValueUsed;

    private String currency = "USD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "brickeconomy_json", columnDefinition = "jsonb")
    private String brickeconomyJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CatalogItem create(String setNumber) {
        CatalogItem item = new CatalogItem();
        item.id = UUID.randomUUID();
        item.setNumber = setNumber;
        item.createdAt = Instant.now();
        item.updatedAt = Instant.now();
        item.fetchedAt = Instant.now();
        return item;
    }

    public void touch() {
        this.updatedAt = Instant.now();
        this.fetchedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(String setNumber) {
        this.setNumber = setNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getSubtheme() {
        return subtheme;
    }

    public void setSubtheme(String subtheme) {
        this.subtheme = subtheme;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getPiecesCount() {
        return piecesCount;
    }

    public void setPiecesCount(Integer piecesCount) {
        this.piecesCount = piecesCount;
    }

    public Integer getMinifigsCount() {
        return minifigsCount;
    }

    public void setMinifigsCount(Integer minifigsCount) {
        this.minifigsCount = minifigsCount;
    }

    public String getUpc() {
        return upc;
    }

    public void setUpc(String upc) {
        this.upc = upc;
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public Boolean getRetired() {
        return retired;
    }

    public void setRetired(Boolean retired) {
        this.retired = retired;
    }

    public LocalDate getRetiredDate() {
        return retiredDate;
    }

    public void setRetiredDate(LocalDate retiredDate) {
        this.retiredDate = retiredDate;
    }

    public LocalDate getReleasedDate() {
        return releasedDate;
    }

    public void setReleasedDate(LocalDate releasedDate) {
        this.releasedDate = releasedDate;
    }

    public BigDecimal getCurrentValueNew() {
        return currentValueNew;
    }

    public void setCurrentValueNew(BigDecimal currentValueNew) {
        this.currentValueNew = currentValueNew;
    }

    public BigDecimal getRetailPriceUs() {
        return retailPriceUs;
    }

    public void setRetailPriceUs(BigDecimal retailPriceUs) {
        this.retailPriceUs = retailPriceUs;
    }

    public BigDecimal getCurrentValueUsed() {
        return currentValueUsed;
    }

    public void setCurrentValueUsed(BigDecimal currentValueUsed) {
        this.currentValueUsed = currentValueUsed;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBrickeconomyJson() {
        return brickeconomyJson;
    }

    public void setBrickeconomyJson(String brickeconomyJson) {
        this.brickeconomyJson = brickeconomyJson;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
