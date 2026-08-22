package com.thetimelessvault.inventory;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.publish.ChannelListing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id")
    private CatalogItem catalogItem;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    private List<ChannelListing> channelListings = new ArrayList<>();

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "short_description", length = 255)
    private String shortDescription;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "ebay_price", nullable = false)
    private BigDecimal ebayPrice;

    @Column(name = "bricklink_price", nullable = false)
    private BigDecimal bricklinkPrice;

    @Column(name = "shopify_price", nullable = false)
    private BigDecimal shopifyPrice;

    @Column(nullable = false)
    private int quantity = 0;

    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType = ItemType.SET;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemCondition condition = ItemCondition.NEW_SEALED;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false)
    private StockStatus stockStatus = StockStatus.IN_TRANSIT;

    @Column(name = "shopify_collection_ids", columnDefinition = "text")
    private String shopifyCollectionIds;

    @Column(name = "ebay_store_category")
    private String ebayStoreCategory;

    @Column(name = "minimum_offer")
    private BigDecimal minimumOffer;

    @Column(name = "package_lbs", nullable = false)
    private int packageLbs;

    @Column(name = "package_oz", nullable = false)
    private int packageOz;

    @Column(name = "package_length")
    private BigDecimal packageLength;

    @Column(name = "package_width")
    private BigDecimal packageWidth;

    @Column(name = "package_height")
    private BigDecimal packageHeight;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static InventoryItem create(CatalogItem catalog, String sku) {
        InventoryItem item = new InventoryItem();
        item.id = UUID.randomUUID();
        item.catalogItem = catalog;
        item.sku = sku;
        item.createdAt = Instant.now();
        item.updatedAt = Instant.now();
        return item;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getEbayPrice() {
        return ebayPrice;
    }

    public void setEbayPrice(BigDecimal ebayPrice) {
        this.ebayPrice = ebayPrice;
    }

    public BigDecimal getBricklinkPrice() {
        return bricklinkPrice;
    }

    public void setBricklinkPrice(BigDecimal bricklinkPrice) {
        this.bricklinkPrice = bricklinkPrice;
    }

    public BigDecimal getShopifyPrice() {
        return shopifyPrice;
    }

    public void setShopifyPrice(BigDecimal shopifyPrice) {
        this.shopifyPrice = shopifyPrice;
    }

    public BigDecimal priceFor(Platform platform) {
        return switch (platform) {
            case EBAY -> ebayPrice;
            case BRICKLINK -> bricklinkPrice;
            case SHOPIFY -> shopifyPrice;
            case LOCAL -> price;
        };
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public StockStatus getStockStatus() {
        return stockStatus;
    }

    public void setStockStatus(StockStatus stockStatus) {
        this.stockStatus = stockStatus;
    }

    public void applyStockAndQuantity(StockStatus nextStatus, Integer requestedQuantity) {
        StockStatus previous = stockStatus == null ? StockStatus.IN_TRANSIT : stockStatus;
        int previousQuantity = quantity;
        if (requestedQuantity != null) {
            quantity = Math.max(0, requestedQuantity);
        }
        if (nextStatus != null) {
            stockStatus = nextStatus;
        }
        if (stockStatus == StockStatus.SOLD || stockStatus == StockStatus.IN_TRANSIT) {
            quantity = 0;
        } else if (stockStatus == StockStatus.IN_STOCK && previous != StockStatus.IN_STOCK) {
            quantity = Math.max(quantity, previousQuantity + 1);
        }
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public void setCondition(ItemCondition condition) {
        this.condition = condition;
    }

    public String getShopifyCollectionIds() {
        return shopifyCollectionIds;
    }

    public void setShopifyCollectionIds(String shopifyCollectionIds) {
        this.shopifyCollectionIds = shopifyCollectionIds;
    }

    public String getEbayStoreCategory() {
        return ebayStoreCategory;
    }

    public void setEbayStoreCategory(String ebayStoreCategory) {
        this.ebayStoreCategory = ebayStoreCategory;
    }

    public BigDecimal getMinimumOffer() {
        return minimumOffer;
    }

    public void setMinimumOffer(BigDecimal minimumOffer) {
        this.minimumOffer = minimumOffer;
    }

    public int getPackageLbs() {
        return packageLbs;
    }

    public void setPackageLbs(int packageLbs) {
        this.packageLbs = packageLbs;
    }

    public int getPackageOz() {
        return packageOz;
    }

    public void setPackageOz(int packageOz) {
        this.packageOz = packageOz;
    }

    public BigDecimal getPackageLength() {
        return packageLength;
    }

    public void setPackageLength(BigDecimal packageLength) {
        this.packageLength = packageLength;
    }

    public BigDecimal getPackageWidth() {
        return packageWidth;
    }

    public void setPackageWidth(BigDecimal packageWidth) {
        this.packageWidth = packageWidth;
    }

    public BigDecimal getPackageHeight() {
        return packageHeight;
    }

    public void setPackageHeight(BigDecimal packageHeight) {
        this.packageHeight = packageHeight;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
