package com.thetimelessvault.sales;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.ebay.EbayClient;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.publish.ChannelListing;
import com.thetimelessvault.publish.ChannelListingRepository;
import com.thetimelessvault.publish.PublishService;
import com.thetimelessvault.settings.ChannelFeeRates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class SalesService {

    private static final Logger log = LoggerFactory.getLogger(SalesService.class);

    private final SaleRepository sales;
    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final CatalogService catalogService;
    private final AppSettingRepository settings;
    private final PublishService publishService;
    private final ChannelFeeRates feeRates;

    public SalesService(
            SaleRepository sales,
            InventoryItemRepository items,
            ChannelListingRepository listings,
            CatalogService catalogService,
            AppSettingRepository settings,
            PublishService publishService,
            ChannelFeeRates feeRates
    ) {
        this.sales = sales;
        this.items = items;
        this.listings = listings;
        this.catalogService = catalogService;
        this.settings = settings;
        this.publishService = publishService;
        this.feeRates = feeRates;
    }

    public Page<Sale> list(int page, int size) {
        int pageSize = Math.min(10_000, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        return sales.findAllByOrderBySoldAtDesc(PageRequest.of(pageIndex, pageSize));
    }

    public Optional<Instant> lastSyncedAt() {
        return settings.findById(SalesSyncService.LAST_SYNC_KEY)
                .map(AppSetting::getValue)
                .map(SalesService::parseInstant);
    }

    @Transactional
    public boolean importSale(ChannelSale incoming) {
        if (incoming == null || incoming.orderId() == null) {
            return false;
        }
        Optional<Sale> existing = sales.findByPlatformAndExternalOrderIdAndExternalLineId(
                incoming.platform(), incoming.orderId(), incoming.identityLineId());
        if (existing.isPresent()) {
            existing.get().applyChannelCosts(incoming);
            sales.save(existing.get());
            return false;
        }
        try {
            add(incoming);
            return true;
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.BAD_REQUEST || e.getStatus() == HttpStatus.CONFLICT) {
                return false;
            }
            throw e;
        }
    }

    @Transactional
    public Sale addManual(ManualSaleRequest request) {
        if (request == null || request.platform() == null) {
            throw ApiException.badRequest("Channel is required");
        }
        if (blank(request.sku())) {
            throw ApiException.badRequest("SKU is required");
        }
        String orderId = blank(request.externalOrderId())
                ? "MANUAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : request.externalOrderId().trim();
        return add(new ChannelSale(
                request.platform(),
                orderId,
                "manual",
                request.sku(),
                null,
                request.itemTitle(),
                request.setNumber(),
                request.quantity() == null ? 1 : request.quantity(),
                request.unitPrice(),
                request.currency(),
                request.soldAt(),
                request.orderUrl(),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ));
    }

    @Transactional
    public void delete(UUID id) {
        if (!sales.existsById(id)) {
            throw ApiException.notFound("Sale not found");
        }
        sales.deleteById(id);
    }

    @Transactional
    Sale add(ChannelSale incoming) {
        if (incoming == null || incoming.orderId() == null) {
            throw ApiException.badRequest("Order is required");
        }
        if (sales.existsByPlatformAndExternalOrderIdAndExternalLineId(
                incoming.platform(), incoming.orderId(), incoming.identityLineId())) {
            throw ApiException.conflict("A sale for that channel order already exists");
        }
        InventoryItem item = resolveItem(incoming);
        boolean created = false;
        if (item == null) {
            CreatedItem createdItem = createSoldItem(incoming);
            item = createdItem.item();
            created = createdItem.created();
        }
        ChannelSale recorded = withRecordedFee(incoming);
        try {
            Sale saved = sales.save(Sale.create(item, recorded, created));
            if (!created) {
                deactivateListings(item, incoming.platform());
                markSold(item);
            }
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("A sale for that channel order already exists");
        }
    }

    public record ManualSaleRequest(
            Platform platform,
            String sku,
            String setNumber,
            String itemTitle,
            String externalOrderId,
            String orderUrl,
            Integer quantity,
            BigDecimal unitPrice,
            String currency,
            Instant soldAt
    ) {
    }

    InventoryItem resolveItem(ChannelSale incoming) {
        if (incoming.sku() != null) {
            Optional<InventoryItem> bySku = items.findWithCatalogBySkuIgnoreCase(incoming.sku());
            if (bySku.isPresent()) {
                return bySku.get();
            }
        }
        if (incoming.listingExternalId() != null) {
            Optional<ChannelListing> byExternal = listings.findByPlatformAndExternalId(
                    incoming.platform(), incoming.listingExternalId());
            if (byExternal.isPresent()) {
                return byExternal.get().getInventoryItem();
            }
            if (incoming.platform() == Platform.EBAY) {
                Optional<ChannelListing> byUrl = listings.findByPlatformAndLiveUrl(
                        Platform.EBAY, EbayClient.listingUrl(incoming.listingExternalId()));
                if (byUrl.isPresent()) {
                    return byUrl.get().getInventoryItem();
                }
            }
        }
        return null;
    }

    CreatedItem createSoldItem(ChannelSale incoming) {
        String setNumber = SetNumberParser.firstNonBlank(
                incoming.setNumber(),
                SetNumberParser.fromSku(incoming.sku()),
                SetNumberParser.fromTitle(incoming.title()),
                "UNKNOWN"
        );
        CatalogItem catalog = catalogService.lookupOrStub(setNumber, incoming.title());
        String sku = incoming.sku() != null
                ? incoming.sku()
                : "TTV-" + catalog.getSetNumber().replaceAll("[^A-Za-z0-9-]", "") + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        Optional<InventoryItem> existingSku = items.findWithCatalogBySkuIgnoreCase(sku);
        if (existingSku.isPresent()) {
            return new CreatedItem(existingSku.get(), false);
        }
        InventoryItem item = InventoryItem.create(catalog, sku);
        BigDecimal price = incoming.unitPrice() == null ? BigDecimal.ZERO : incoming.unitPrice();
        String title = incoming.title() == null || incoming.title().isBlank()
                ? ThemeMapper.suggestedTitle(
                        catalog.getTheme(), catalog.getSetNumber(), catalog.getName(), ItemCondition.NEW_SEALED)
                : incoming.title();
        item.setTitle(ThemeMapper.limitTitle(title));
        item.setEbayPrice(price);
        item.setBricklinkPrice(price);
        item.setShopifyPrice(price);
        item.setPrice(price);
        item.setItemType(ItemType.SET);
        item.setCondition(ItemCondition.NEW_SEALED);
        item.applyStockAndQuantity(StockStatus.SOLD, 0);
        item.setPackageLbs(0);
        item.setPackageOz(0);
        item.setNotes("Imported from " + incoming.platform().name() + " order " + incoming.orderId());
        return new CreatedItem(items.save(item), true);
    }

    record CreatedItem(InventoryItem item, boolean created) {
    }

    private ChannelSale withRecordedFee(ChannelSale incoming) {
        BigDecimal fee = feeRates.feeFor(
                incoming.platform(), incoming.unitPrice(), incoming.quantity(), incoming.shippingCost());
        return fee == null ? incoming : incoming.withPlatformFee(fee);
    }

    private void deactivateListings(InventoryItem item, Platform soldOn) {
        try {
            publishService.deactivatePublishedListingsAfterSale(item.getId(), soldOn);
        } catch (RuntimeException e) {
            log.warn("Could not deactivate listings after sale for item {}: {}", item.getId(), e.getMessage());
        }
    }

    private void markSold(InventoryItem item) {
        item.applyStockAndQuantity(StockStatus.SOLD, 0);
        items.save(item);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
