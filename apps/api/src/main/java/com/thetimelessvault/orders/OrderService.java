package com.thetimelessvault.orders;

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
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.publish.PublishService;
import com.thetimelessvault.settings.ChannelFeeRates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OrderIgnoreRepository ignores;
    private final InventoryItemRepository items;
    private final ChannelListingRepository listings;
    private final CatalogService catalogService;
    private final AppSettingRepository settings;
    private final PublishService publishService;
    private final BuyingOpportunityService opportunities;
    private final ChannelFeeRates feeRates;

    public OrderService(
            OrderRepository orders,
            OrderIgnoreRepository ignores,
            InventoryItemRepository items,
            ChannelListingRepository listings,
            CatalogService catalogService,
            AppSettingRepository settings,
            PublishService publishService,
            BuyingOpportunityService opportunities,
            ChannelFeeRates feeRates
    ) {
        this.orders = orders;
        this.ignores = ignores;
        this.items = items;
        this.listings = listings;
        this.catalogService = catalogService;
        this.settings = settings;
        this.publishService = publishService;
        this.opportunities = opportunities;
        this.feeRates = feeRates;
    }

    @Transactional(readOnly = true)
    public Page<Order> list(int page, int size) {
        return list(page, size, OrderSpecifications.Query.empty());
    }

    @Transactional(readOnly = true)
    public Page<Order> list(int page, int size, OrderSpecifications.Query query) {
        int pageSize = Math.min(10_000, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        Page<Order> result = orders.findAll(
                OrderSpecifications.matching(query),
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        result.getContent().forEach(order -> {
            order.getLines().size();
            loadTrackings(order);
        });
        return result;
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return loadTrackings(orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found")));
    }

    public Optional<Instant> lastSyncedAt() {
        return settings.findById(OrderSyncService.LAST_SYNC_KEY)
                .map(AppSetting::getValue)
                .map(OrderService::parseInstant);
    }

    @Transactional
    public boolean importOrder(ChannelOrder incoming) {
        if (incoming == null || incoming.orderId() == null) {
            return false;
        }
        if (ignored(incoming.platform(), incoming.orderId())) {
            return false;
        }
        Optional<Order> existing = orders.findByPlatformAndExternalOrderId(incoming.platform(), incoming.orderId());
        if (existing.isPresent()) {
            Order order = existing.get();
            OrderStatus previous = order.applyChannelUpdate(incoming);
            boolean added = attachLine(order, incoming);
            if (added) {
                refreshCalculatedFee(order);
            }
            orders.save(order);
            afterStatusChange(order, previous);
            return added;
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
    public Order addManual(ManualOrderRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Order is required");
        }
        if (blank(request.sku())) {
            throw ApiException.badRequest("SKU is required");
        }
        Platform platform = request.platform() == null ? Platform.LOCAL : request.platform();
        String orderId = blank(request.externalOrderId())
                ? "MANUAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : request.externalOrderId().trim();
        return add(new ChannelOrder(
                platform,
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
                request.shippingCost(),
                request.platformFee(),
                request.status() == null ? OrderStatus.OPEN : request.status(),
                request.trackingNumber(),
                request.shippingProvider()
        ));
    }

    @Transactional
    public Order update(UUID id, UpdateOrderRequest request) {
        Order order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (request == null) {
            return order;
        }
        OrderStatus previous = order.applyManual(
                request.status(),
                request.trackingNumber(),
                request.shippingProvider(),
                request.trackings()
        );
        orders.save(order);
        afterStatusChange(order, previous);
        return loadTrackings(order);
    }

    @Transactional
    public Order markDeliveredFromCarrier(UUID id) {
        Order order = get(id);
        OrderStatus previous = order.markDeliveredFromCarrier();
        orders.save(order);
        afterStatusChange(order, previous);
        return order;
    }

    @Transactional
    public void delete(UUID id) {
        Order order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        ignore(order.getPlatform(), order.getExternalOrderId());
        orders.delete(order);
    }

    @Transactional
    Order add(ChannelOrder incoming) {
        if (incoming == null || incoming.orderId() == null) {
            throw ApiException.badRequest("Order is required");
        }
        Optional<Order> existing = orders.findByPlatformAndExternalOrderId(incoming.platform(), incoming.orderId());
        if (existing.isPresent()) {
            Order order = existing.get();
            if (order.hasLine(incoming.identityLineId())) {
                throw ApiException.conflict("An order for that channel already exists");
            }
            attachLine(order, incoming);
            refreshCalculatedFee(order);
            return loadTrackings(orders.save(order));
        }
        if (orders.existsByPlatformAndExternalOrderId(incoming.platform(), incoming.orderId())) {
            throw ApiException.conflict("An order for that channel already exists");
        }
        clearIgnore(incoming.platform(), incoming.orderId());
        try {
            Order order = Order.header(incoming);
            attachLine(order, incoming);
            refreshCalculatedFee(order);
            return loadTrackings(orders.save(order));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("An order for that channel already exists");
        }
    }

    private static Order loadTrackings(Order order) {
        order.getTrackings().size();
        return order;
    }

    public record ManualOrderRequest(
            Platform platform,
            String sku,
            String setNumber,
            String itemTitle,
            String externalOrderId,
            String orderUrl,
            Integer quantity,
            BigDecimal unitPrice,
            String currency,
            Instant soldAt,
            OrderStatus status,
            String trackingNumber,
            String shippingProvider,
            BigDecimal shippingCost,
            BigDecimal platformFee
    ) {
    }

    public record UpdateOrderRequest(
            OrderStatus status,
            String trackingNumber,
            String shippingProvider,
            List<ShipmentTracking> trackings
    ) {
    }

    private boolean attachLine(Order order, ChannelOrder incoming) {
        if (order.hasLine(incoming.identityLineId())) {
            return false;
        }
        boolean cancelled = incoming.status() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.CANCELLED;
        InventoryItem item = resolveItem(incoming);
        if (incoming.platform() == Platform.EBAY && alreadyOnEbayOrder(order, incoming, item)) {
            return false;
        }
        boolean created = false;
        if (item == null && !cancelled) {
            CreatedItem createdItem = createSoldItem(incoming);
            item = createdItem.item();
            created = createdItem.created();
        }
        OrderLine line = OrderLine.create(item, incoming, created);
        order.addLine(line);
        if (!cancelled && !created && item != null) {
            deactivateListings(item, incoming.platform());
            markSold(item);
        }
        if (!cancelled) {
            opportunities.recordNewSale(order, item);
        }
        return true;
    }

    private static boolean alreadyOnEbayOrder(Order order, ChannelOrder incoming, InventoryItem item) {
        if (item != null && order.hasInventoryItem(item.getId())) {
            return true;
        }
        if (incoming.sku() != null && order.hasSku(incoming.sku())) {
            return true;
        }
        if (incoming.sku() != null) {
            return false;
        }
        if (incoming.title() != null && order.hasTitle(incoming.title())) {
            return true;
        }
        return item == null && !order.getLines().isEmpty();
    }

    private void refreshCalculatedFee(Order order) {
        if (order.getPlatformFee() != null && order.getPlatformFee().signum() > 0
                && feeRates.feeFor(order.getPlatform(), order.merchandiseTotal(), 1, order.getShippingCost()) == null) {
            return;
        }
        BigDecimal fee = feeRates.feeFor(
                order.getPlatform(), order.merchandiseTotal(), 1, order.getShippingCost());
        if (fee != null) {
            order.setPlatformFee(fee);
        }
    }

    private void afterStatusChange(Order order, OrderStatus previous) {
        if (order == null) {
            return;
        }
        OrderStatus current = order.getStatus();
        if (previous == current) {
            return;
        }
        applyInventoryTransition(order, previous, current);
        if (current == OrderStatus.COMPLETED) {
            opportunities.recordOrderDelivered(order, firstLinkedItem(order));
        }
    }

    private InventoryItem firstLinkedItem(Order order) {
        for (OrderLine line : order.getLines()) {
            InventoryItem item = loadItem(line);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private InventoryItem loadItem(OrderLine line) {
        if (line == null || line.getInventoryItemId() == null) {
            return null;
        }
        return items.findById(line.getInventoryItemId()).orElse(null);
    }

    private void applyInventoryTransition(Order order, OrderStatus from, OrderStatus to) {
        for (OrderLine line : order.getLines()) {
            InventoryItem item = loadItem(line);
            if (item == null) {
                continue;
            }
            if (to == OrderStatus.CANCELLED && from != OrderStatus.CANCELLED) {
                item.applyStockAndQuantity(StockStatus.IN_STOCK, 1);
                items.save(item);
                continue;
            }
            if (from == OrderStatus.CANCELLED && to != OrderStatus.CANCELLED) {
                deactivateListings(item, order.getPlatform());
                markSold(item);
            }
        }
    }

    private void ignore(Platform platform, String orderId) {
        if (ignored(platform, orderId)) {
            return;
        }
        ignores.save(OrderIgnore.of(platform, orderId, "*"));
    }

    private void clearIgnore(Platform platform, String orderId) {
        ignores.findByPlatformAndExternalOrderId(platform, orderId).forEach(ignores::delete);
    }

    private boolean ignored(Platform platform, String orderId) {
        return ignores.existsByPlatformAndExternalOrderId(platform, orderId);
    }

    InventoryItem resolveItem(ChannelOrder incoming) {
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

    CreatedItem createSoldItem(ChannelOrder incoming) {
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
        item.setBrickowlPrice(price);
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

    private void deactivateListings(InventoryItem item, Platform soldOn) {
        try {
            publishService.deactivatePublishedListingsAfterSale(item.getId(), soldOn);
        } catch (RuntimeException e) {
            log.warn("Could not deactivate listings after order for item {}: {}", item.getId(), e.getMessage());
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
