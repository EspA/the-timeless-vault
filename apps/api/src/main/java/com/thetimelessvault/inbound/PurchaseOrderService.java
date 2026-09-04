package com.thetimelessvault.inbound;

import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.DefaultListingCopy;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ItemType;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.common.StockStatus;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.inventory.ChannelPrices;
import com.thetimelessvault.inventory.InventoryDtos;
import com.thetimelessvault.inventory.InventoryItem;
import com.thetimelessvault.inventory.InventoryItemRepository;
import com.thetimelessvault.inventory.InventoryService;
import com.thetimelessvault.opportunities.BuyingOpportunityService;
import com.thetimelessvault.publish.PublishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderService.class);

    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final InventoryService inventory;
    private final InventoryItemRepository items;
    private final CatalogService catalogService;
    private final BrickLinkClient brickLinkClient;
    private final PublishService publishService;
    private final BuyingOpportunityService opportunities;

    public PurchaseOrderService(
            PurchaseOrderRepository orders,
            SupplierRepository suppliers,
            InventoryService inventory,
            InventoryItemRepository items,
            CatalogService catalogService,
            BrickLinkClient brickLinkClient,
            PublishService publishService,
            BuyingOpportunityService opportunities
    ) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.inventory = inventory;
        this.items = items;
        this.catalogService = catalogService;
        this.brickLinkClient = brickLinkClient;
        this.publishService = publishService;
        this.opportunities = opportunities;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> list(int page, int size) {
        int pageSize = Math.min(10_000, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        Page<PurchaseOrder> result = orders.findAll(PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        result.getContent().forEach(PurchaseOrderService::loadTrackings);
        return result;
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(UUID id) {
        return loadTrackings(orders.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Purchase order not found")));
    }

    private static PurchaseOrder loadTrackings(PurchaseOrder order) {
        order.getTrackings().size();
        return order;
    }

    @Transactional
    public PurchaseOrder create(UpsertRequest request) {
        List<LineRequest> lines = requireLines(request.lines());
        PurchaseOrder order = PurchaseOrder.create(supplier(request.supplierId()), (int) orders.nextPoNumber());
        applyHeader(order, request);
        for (LineRequest lineRequest : lines) {
            order.addLine(newLine(lineRequest));
        }
        return orders.save(order);
    }

    @Transactional
    public PurchaseOrder update(UUID id, UpsertRequest request) {
        PurchaseOrder order = get(id);
        requireOpen(order);
        order.setSupplier(supplier(request.supplierId()));
        applyHeader(order, request);
        syncLines(order, requireLines(request.lines()));
        order.touch();
        return orders.save(order);
    }

    @Transactional
    public PurchaseOrder applyExpectedArrival(UUID id, LocalDate expectedArrival) {
        if (expectedArrival == null) {
            return get(id);
        }
        PurchaseOrder order = get(id);
        if (!order.isOpen()) {
            return order;
        }
        if (expectedArrival.equals(order.getExpectedArrival())) {
            return order;
        }
        order.setExpectedArrival(expectedArrival);
        order.touch();
        log.info("Updated {} expected arrival to {}", order.displayNumber(), expectedArrival);
        return orders.save(order);
    }

    @Transactional
    public PurchaseOrder receive(UUID id) {
        PurchaseOrder order = get(id);
        requireOpen(order);
        if (order.getLines().isEmpty()) {
            throw ApiException.badRequest("A purchase order needs at least one line before it can be received");
        }
        for (PurchaseOrderLine line : order.getLines()) {
            receiveLine(line);
        }
        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.touch();
        return orders.save(order);
    }

    @Transactional
    public PurchaseOrder markDelivered(UUID id) {
        PurchaseOrder order = get(id);
        requireOpen(order);
        if (order.getStatus() == PurchaseOrderStatus.DELIVERED) {
            return order;
        }
        order.setStatus(PurchaseOrderStatus.DELIVERED);
        order.touch();
        PurchaseOrder saved = orders.save(order);
        opportunities.recordPurchaseOrderDelivered(saved, firstLinkedItem(saved));
        return saved;
    }

    @Transactional
    public PurchaseOrder cancel(UUID id) {
        PurchaseOrder order = get(id);
        requireOpen(order);
        List<UUID> itemIds = order.getLines().stream()
                .map(PurchaseOrderLine::getInventoryItemId)
                .filter(Objects::nonNull)
                .toList();
        for (PurchaseOrderLine line : order.getLines()) {
            line.setInventoryItemId(null);
            line.touch();
        }
        orders.saveAndFlush(order);
        itemIds.forEach(this::purgeInventory);
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.touch();
        return orders.save(order);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDtos.PurchaseOrderView view(UUID id) {
        return toView(get(id));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDtos.PurchaseOrderPage page(int page, int size) {
        var result = list(page, size);
        return new PurchaseOrderDtos.PurchaseOrderPage(
                result.getContent().stream().map(this::toView).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                Math.max(1, result.getTotalPages())
        );
    }

    @Transactional
    public PurchaseOrderDtos.PurchaseOrderView createView(UpsertRequest request) {
        return toView(create(request));
    }

    @Transactional
    public PurchaseOrderDtos.PurchaseOrderView updateView(UUID id, UpsertRequest request) {
        return toView(update(id, request));
    }

    @Transactional
    public PurchaseOrder updateHeader(UUID id, HeaderRequest request) {
        PurchaseOrder order = get(id);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw ApiException.badRequest("Cancelled purchase orders cannot be changed");
        }
        order.setExpectedArrival(request.expectedArrival());
        applyTrackings(order, request.trackingNumber(), request.carrier(), request.trackings());
        order.touch();
        return orders.save(order);
    }

    @Transactional
    public PurchaseOrderDtos.PurchaseOrderView updateHeaderView(UUID id, HeaderRequest request) {
        return toView(updateHeader(id, request));
    }

    @Transactional
    public PurchaseOrderDtos.PurchaseOrderView receiveView(UUID id) {
        return toView(receive(id));
    }

    @Transactional
    public PurchaseOrderDtos.PurchaseOrderView markDeliveredView(UUID id) {
        return toView(markDelivered(id));
    }

    @Transactional
    public PurchaseOrderDtos.PurchaseOrderView cancelView(UUID id) {
        return toView(cancel(id));
    }

    private PurchaseOrderDtos.PurchaseOrderView toView(PurchaseOrder order) {
        List<PurchaseOrderDtos.LineView> lines = order.getLines().stream()
                .map(line -> new PurchaseOrderDtos.LineView(
                        line.getId(),
                        line.getSetNumber(),
                        line.getTitle(),
                        line.getQuantity(),
                        line.getUnitValue(),
                        line.lineTotal(),
                        line.getInventoryItemId(),
                        skuFor(line)
                ))
                .toList();
        return new PurchaseOrderDtos.PurchaseOrderView(
                order.getId(),
                order.displayNumber(),
                order.getSupplier().getId(),
                order.getSupplier().getName(),
                order.getStatus(),
                order.totalValue(),
                order.getExpectedArrival(),
                order.getTrackingNumber(),
                order.getCarrier(),
                order.getTrackings().stream().map(PurchaseOrderDtos.TrackingView::from).toList(),
                order.getNote(),
                lines,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private void syncLines(PurchaseOrder order, List<LineRequest> requests) {
        Map<UUID, PurchaseOrderLine> existing = order.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));
        Set<UUID> keep = new HashSet<>();
        List<PurchaseOrderLine> removed = new ArrayList<>();
        for (LineRequest request : requests) {
            if (request.id() == null) {
                order.addLine(newLine(request));
                continue;
            }
            PurchaseOrderLine line = existing.get(request.id());
            if (line == null) {
                throw ApiException.badRequest("Unknown purchase order line");
            }
            keep.add(line.getId());
            updateLine(order, line, request);
        }
        for (PurchaseOrderLine line : List.copyOf(order.getLines())) {
            if (!keep.contains(line.getId()) && existing.containsKey(line.getId())) {
                removed.add(line);
                order.getLines().remove(line);
            }
        }
        orders.saveAndFlush(order);
        for (PurchaseOrderLine line : removed) {
            if (line.getInventoryItemId() != null) {
                purgeInventory(line.getInventoryItemId());
            }
        }
    }

    private PurchaseOrderLine newLine(LineRequest request) {
        LineDraft draft = draft(request);
        PurchaseOrderLine line = PurchaseOrderLine.create(
                draft.setNumber(), draft.title(), draft.quantity(), draft.unitValue());
        line.setInventoryItemId(createInventory(draft).getId());
        return line;
    }

    private void updateLine(PurchaseOrder order, PurchaseOrderLine line, LineRequest request) {
        LineDraft draft = draft(request);
        boolean setChanged = !line.getSetNumber().equalsIgnoreCase(draft.setNumber());
        if (setChanged) {
            UUID previous = line.getInventoryItemId();
            line.setInventoryItemId(null);
            orders.saveAndFlush(order);
            if (previous != null) {
                purgeInventory(previous);
            }
            line.setSetNumber(draft.setNumber());
            line.setTitle(draft.title());
            line.setQuantity(draft.quantity());
            line.setUnitValue(draft.unitValue());
            line.setInventoryItemId(createInventory(draft).getId());
            line.touch();
            return;
        }
        line.setTitle(draft.title());
        line.setQuantity(draft.quantity());
        line.setUnitValue(draft.unitValue());
        line.touch();
        if (line.getInventoryItemId() != null && items.existsById(line.getInventoryItemId())) {
            updateInventory(line.getInventoryItemId(), draft);
        } else {
            line.setInventoryItemId(createInventory(draft).getId());
        }
    }

    private void receiveLine(PurchaseOrderLine line) {
        if (line.getInventoryItemId() == null || !items.existsById(line.getInventoryItemId())) {
            return;
        }
        InventoryItem item = inventory.get(line.getInventoryItemId());
        item.setTitle(line.getTitle());
        item.setCost(line.getUnitValue());
        applyChannelPrices(item, line.getUnitValue());
        item.applyStockAndQuantity(StockStatus.IN_STOCK, line.getQuantity());
        item.touch();
        items.save(item);
    }

    private InventoryItem createInventory(LineDraft draft) {
        CatalogItem catalog = catalogService.lookup(draft.setNumber(), false);
        BigDecimal ebay = ChannelPrices.ebay(draft.unitValue());
        InventoryDtos.BrickLinkMeasures shipping = shipping(catalog.getSetNumber());
        String description = DefaultListingCopy.description(catalog);
        return inventory.create(new InventoryDtos.CreateRequest(
                catalog.getSetNumber(),
                draft.title(),
                description,
                DefaultListingCopy.shortDescription(catalog),
                ebay,
                ebay,
                ChannelPrices.bricklink(draft.unitValue()),
                ChannelPrices.shopify(draft.unitValue()),
                ChannelPrices.brickowl(draft.unitValue()),
                draft.quantity(),
                draft.unitValue(),
                ItemType.SET,
                ItemCondition.NEW_SEALED,
                StockStatus.IN_TRANSIT,
                List.of(),
                ThemeMapper.ebayStoreCategory(catalog.getTheme()),
                ChannelPrices.minimumOffer(ebay),
                shipping == null ? 0 : shipping.lbs(),
                0,
                shipping == null ? null : shipping.length(),
                shipping == null ? null : shipping.width(),
                shipping == null ? null : shipping.height(),
                null
        ));
    }

    private void updateInventory(UUID itemId, LineDraft draft) {
        BigDecimal ebay = ChannelPrices.ebay(draft.unitValue());
        inventory.update(itemId, new InventoryDtos.UpdateRequest(
                draft.title(),
                null,
                null,
                ebay,
                ebay,
                ChannelPrices.bricklink(draft.unitValue()),
                ChannelPrices.shopify(draft.unitValue()),
                ChannelPrices.brickowl(draft.unitValue()),
                draft.quantity(),
                draft.unitValue(),
                null,
                null,
                StockStatus.IN_TRANSIT,
                null,
                null,
                ChannelPrices.minimumOffer(ebay),
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    private void applyChannelPrices(InventoryItem item, BigDecimal cost) {
        BigDecimal ebay = ChannelPrices.ebay(cost);
        item.setEbayPrice(ebay);
        item.setPrice(ebay);
        item.setBricklinkPrice(ChannelPrices.bricklink(cost));
        item.setShopifyPrice(ChannelPrices.shopify(cost));
        item.setBrickowlPrice(ChannelPrices.brickowl(cost));
        item.setMinimumOffer(ChannelPrices.minimumOffer(ebay));
    }

    private void purgeInventory(UUID itemId) {
        if (!items.existsById(itemId)) {
            return;
        }
        try {
            publishService.deactivatePublishedListings(itemId);
        } catch (RuntimeException e) {
            log.warn("Could not deactivate listings for inventory item {}", itemId, e);
        }
        publishService.deleteChannelListingIfPresent(itemId, Platform.SHOPIFY);
        publishService.deleteChannelListingIfPresent(itemId, Platform.BRICKLINK);
        publishService.deleteChannelListingIfPresent(itemId, Platform.BRICKOWL);
        publishService.deleteChannelListingIfPresent(itemId, Platform.EBAY);
        inventory.delete(itemId);
    }

    private InventoryDtos.BrickLinkMeasures shipping(String setNumber) {
        try {
            InventoryDtos.BrickLinkPackage pkg = brickLinkClient.packageForSet(setNumber);
            return pkg == null ? null : pkg.shipping();
        } catch (RuntimeException e) {
            log.debug("No BrickLink package for {}", setNumber, e);
            return null;
        }
    }

    private LineDraft draft(LineRequest request) {
        if (request.setNumber() == null || request.setNumber().isBlank()) {
            throw ApiException.badRequest("Set number is required on each line");
        }
        if (request.unitValue() == null || request.unitValue().signum() <= 0) {
            throw ApiException.badRequest("Unit value must be greater than 0");
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (quantity < 1) {
            throw ApiException.badRequest("Quantity must be at least 1");
        }
        CatalogItem catalog = catalogService.lookup(request.setNumber().trim(), false);
        String title = request.title() == null || request.title().isBlank()
                ? ThemeMapper.suggestedTitle(catalog.getTheme(), catalog.getSubtheme(), catalog.getSetNumber(), catalog.getName(), ItemCondition.NEW_SEALED)
                : ThemeMapper.limitTitle(request.title());
        return new LineDraft(catalog.getSetNumber(), title, quantity, request.unitValue().setScale(2, RoundingMode.HALF_UP));
    }

    private static List<LineRequest> requireLines(List<LineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw ApiException.badRequest("Add at least one purchase order line");
        }
        return lines;
    }

    private Supplier supplier(UUID supplierId) {
        if (supplierId == null) {
            throw ApiException.badRequest("Supplier is required");
        }
        return suppliers.findById(supplierId).orElseThrow(() -> ApiException.notFound("Supplier not found"));
    }

    private static void applyHeader(PurchaseOrder order, UpsertRequest request) {
        order.setExpectedArrival(request.expectedArrival());
        applyTrackings(order, request.trackingNumber(), request.carrier(), request.trackings());
        order.setNote(blankToNull(request.note()));
    }

    private static void applyTrackings(
            PurchaseOrder order,
            String trackingNumber,
            ShippingCarrier carrier,
            List<PurchaseOrder.TrackingDraft> trackings
    ) {
        if (trackings != null) {
            order.replaceTrackings(trackings);
            return;
        }
        order.replaceTracking(blankToNull(trackingNumber), carrier);
    }

    private static void requireOpen(PurchaseOrder order) {
        if (!order.isOpen()) {
            throw ApiException.badRequest("Received or cancelled purchase orders cannot be changed");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private InventoryItem firstLinkedItem(PurchaseOrder order) {
        for (PurchaseOrderLine line : order.getLines()) {
            if (line.getInventoryItemId() == null) {
                continue;
            }
            InventoryItem item = items.findById(line.getInventoryItemId()).orElse(null);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    public String skuFor(PurchaseOrderLine line) {
        if (line.getInventoryItemId() == null) {
            return null;
        }
        return items.findById(line.getInventoryItemId()).map(InventoryItem::getSku).orElse(null);
    }

    public record LineRequest(UUID id, String setNumber, String title, Integer quantity, BigDecimal unitValue) {
    }

    public record UpsertRequest(
            UUID supplierId,
            LocalDate expectedArrival,
            String trackingNumber,
            ShippingCarrier carrier,
            String note,
            List<LineRequest> lines,
            List<PurchaseOrder.TrackingDraft> trackings
    ) {
    }

    public record HeaderRequest(
            LocalDate expectedArrival,
            String trackingNumber,
            ShippingCarrier carrier,
            List<PurchaseOrder.TrackingDraft> trackings
    ) {
    }

    private record LineDraft(String setNumber, String title, int quantity, BigDecimal unitValue) {
    }
}
