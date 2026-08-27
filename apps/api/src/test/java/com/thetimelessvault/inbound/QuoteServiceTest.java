package com.thetimelessvault.inbound;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.Platform;
import com.thetimelessvault.market.MarketSnapshot;
import com.thetimelessvault.market.MarketSnapshotRepository;
import com.thetimelessvault.market.ScanTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuoteServiceTest {

    @Mock QuoteRepository quotes;
    @Mock CatalogService catalogService;
    @Mock MarketSnapshotRepository snapshots;
    @Mock PurchaseOrderService purchaseOrders;

    QuoteService service;
    Quote current;
    Map<String, CatalogItem> catalogs;

    @BeforeEach
    void setUp() {
        catalogs = new HashMap<>();
        service = new QuoteService(quotes, catalogService, snapshots, purchaseOrders);
        when(quotes.nextQuoteNumber()).thenReturn(1L);
        when(quotes.save(any(Quote.class))).thenAnswer(invocation -> {
            current = invocation.getArgument(0);
            return current;
        });
        when(quotes.findWithDetailsById(any())).thenAnswer(invocation -> Optional.ofNullable(current));
        when(catalogService.lookup(anyString(), eq(false))).thenAnswer(invocation ->
                catalogs.computeIfAbsent(invocation.getArgument(0), this::catalog));
        when(snapshots.findLatestByCatalogItemIdIn(any())).thenReturn(List.of());
    }

    @Test
    void proratesShippingByCostWeightAndComputesLandedCost() {
        QuoteDtos.QuoteView view = service.createView(request(
                "100.00",
                line("79001-1", "250.00"),
                line("79002-1", "300.00"),
                line("79003-1", "100.00")
        ));

        assertEquals("Quote 001", view.number());
        assertEquals(new BigDecimal("650.00"), view.totalCost());
        assertEquals(new BigDecimal("100.00"), view.shippingTotal());
        assertEquals(new BigDecimal("750.00"), view.totalCostWithShipping());
        assertEquals(new BigDecimal("38.46"), view.lines().get(0).proratedShipping());
        assertEquals(new BigDecimal("46.15"), view.lines().get(1).proratedShipping());
        assertEquals(new BigDecimal("15.38"), view.lines().get(2).proratedShipping());
        assertEquals(new BigDecimal("288.46"), view.lines().get(0).costWithShipping());
        assertEquals(new BigDecimal("346.15"), view.lines().get(1).costWithShipping());
        assertEquals(new BigDecimal("115.38"), view.lines().get(2).costWithShipping());
    }

    @Test
    void zeroCostLinesGetNoProratedShipping() {
        QuoteDtos.QuoteView view = service.createView(request(
                "50.00",
                line("79001-1", "100.00"),
                line("79002-1", "0")
        ));

        assertEquals(new BigDecimal("50.00"), view.lines().get(0).proratedShipping());
        assertEquals(new BigDecimal("0.00"), view.lines().get(1).proratedShipping());
        assertEquals(new BigDecimal("0.00"), view.lines().get(1).costWithShipping());
    }

    @Test
    void allZeroCostsLeaveProratedShippingAtZero() {
        QuoteDtos.QuoteView view = service.createView(request(
                "80.00",
                line("79001-1", "0"),
                line("79002-1", "0")
        ));

        assertEquals(new BigDecimal("0.00"), view.lines().get(0).proratedShipping());
        assertEquals(new BigDecimal("0.00"), view.lines().get(1).proratedShipping());
        assertEquals(new BigDecimal("80.00"), view.totalCostWithShipping());
    }

    @Test
    void marginUsesLandedCostAndColorBands() {
        QuoteDtos.QuoteView view = service.createView(request(
                "100.00",
                line("79001-1", "250.00"),
                line("79002-1", "300.00"),
                line("79003-1", "100.00")
        ));
        when(snapshots.findLatestByCatalogItemIdIn(any())).thenAnswer(invocation -> List.of(
                snapshot(catalogs.get("79001-1"), new BigDecimal("410.00"), 1),
                snapshot(catalogs.get("79002-1"), new BigDecimal("440.00"), 1),
                snapshot(catalogs.get("79003-1"), new BigDecimal("130.00"), 1)
        ));

        QuoteDtos.QuoteView priced = service.view(current.getId());
        QuoteDtos.LineView high = priced.lines().get(0);
        QuoteDtos.LineView mid = priced.lines().get(1);
        QuoteDtos.LineView low = priced.lines().get(2);

        assertEquals(new BigDecimal("121.54"), high.marginDollars());
        assertEquals(new BigDecimal("42"), high.marginPercent());
        assertEquals("high", high.marginTone());
        assertEquals(new BigDecimal("93.85"), mid.marginDollars());
        assertEquals(new BigDecimal("27"), mid.marginPercent());
        assertEquals("mid", mid.marginTone());
        assertEquals(new BigDecimal("14.62"), low.marginDollars());
        assertEquals(new BigDecimal("13"), low.marginPercent());
        assertEquals("low", low.marginTone());
        assertEquals(new BigDecimal("230.01"), priced.totalMarginDollars());
        assertEquals(new BigDecimal("31"), priced.averageMarginPercent());
        assertEquals("mid", priced.averageMarginTone());
    }

    @Test
    void combinedMedianAveragesEbayAndBrickLinkSnapshots() {
        CatalogItem catalog = catalog("75192-1");
        when(catalogService.lookup(eq("75192-1"), eq(false))).thenReturn(catalog);
        MarketSnapshot ebay = snapshot(catalog, new BigDecimal("400.00"), 10);
        MarketSnapshot bricklink = MarketSnapshot.create(catalog, Platform.BRICKLINK, "N", ScanTrigger.MANUAL);
        bricklink.setMedianPrice(new BigDecimal("200.00"));
        bricklink.setListingCount(10);
        when(snapshots.findLatestByCatalogItemIdIn(any())).thenReturn(List.of(ebay, bricklink));

        QuoteDtos.QuoteView view = service.createView(request("0", line("75192-1", "100.00")));

        assertEquals(new BigDecimal("300.00"), view.lines().get(0).medianMarketPrice());
    }

    @Test
    void persistsQuoteDescriptionAndClearsBlank() {
        QuoteDtos.QuoteView created = service.createView(new QuoteService.UpsertRequest(
                BigDecimal.ZERO,
                "  BrickLink lot, PayPal  ",
                List.of(line("79001-1", "50.00"))
        ));
        assertEquals("BrickLink lot, PayPal", created.description());

        QuoteDtos.QuoteView cleared = service.updateView(current.getId(), new QuoteService.UpsertRequest(
                BigDecimal.ZERO,
                "   ",
                List.of(new QuoteService.LineRequest(
                        current.getLines().get(0).getId(), "79001-1", "Title 79001-1", new BigDecimal("50.00")))
        ));
        assertNull(cleared.description());
    }

    @Test
    void missingScanLeavesMedianAndMarginBlank() {
        QuoteDtos.QuoteView view = service.createView(request("10.00", line("79001-1", "50.00")));

        assertNull(view.lines().get(0).medianMarketPrice());
        assertNull(view.lines().get(0).marginPercent());
        assertNull(view.lines().get(0).marginTone());
        assertNull(view.averageMarginPercent());
    }

    @Test
    void convertCreatesPurchaseOrderWithLandedCostAndLocksTheQuote() {
        service.createView(request("100.00",
                line("79001-1", "250.00"),
                line("79002-1", "300.00"),
                line("79003-1", "100.00")));
        Supplier supplier = Supplier.create("Brick Depot");
        PurchaseOrder created = PurchaseOrder.create(supplier, 12);
        when(purchaseOrders.create(any())).thenReturn(created);

        QuoteDtos.QuoteView converted = service.convertToPurchaseOrder(current.getId(), new QuoteService.ConvertRequest(
                supplier.getId(),
                ShippingCarrier.UPS,
                "1Z999",
                null,
                null
        ));

        assertEquals(QuoteStatus.CONVERTED, converted.status());
        assertEquals(created.getId(), converted.purchaseOrderId());
        assertEquals("PO-12", converted.purchaseOrderNumber());

        ArgumentCaptor<PurchaseOrderService.UpsertRequest> captor =
                ArgumentCaptor.forClass(PurchaseOrderService.UpsertRequest.class);
        verify(purchaseOrders).create(captor.capture());
        PurchaseOrderService.UpsertRequest po = captor.getValue();
        assertEquals(supplier.getId(), po.supplierId());
        assertEquals(ShippingCarrier.UPS, po.carrier());
        assertEquals("1Z999", po.trackingNumber());
        assertEquals(3, po.lines().size());
        assertEquals(new BigDecimal("288.46"), po.lines().get(0).unitValue());
        assertEquals(new BigDecimal("346.15"), po.lines().get(1).unitValue());
        assertEquals(new BigDecimal("115.38"), po.lines().get(2).unitValue());
        assertEquals(1, po.lines().get(0).quantity());
    }

    @Test
    void convertOnce() {
        service.createView(request("0", line("79001-1", "50.00")));
        PurchaseOrder created = PurchaseOrder.create(Supplier.create("Brick Depot"), 3);
        when(purchaseOrders.create(any())).thenReturn(created);
        service.convertToPurchaseOrder(current.getId(), new QuoteService.ConvertRequest(
                created.getSupplier().getId(), ShippingCarrier.UPS, "1Z", null, null));

        ApiException error = assertThrows(ApiException.class, () -> service.convertToPurchaseOrder(
                current.getId(),
                new QuoteService.ConvertRequest(created.getSupplier().getId(), ShippingCarrier.UPS, "1Z", null, null)
        ));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(purchaseOrders).create(any());
    }

    @Test
    void convertedQuoteCannotBeUpdated() {
        service.createView(request("0", line("79001-1", "50.00")));
        PurchaseOrder created = PurchaseOrder.create(Supplier.create("Brick Depot"), 3);
        when(purchaseOrders.create(any())).thenReturn(created);
        service.convertToPurchaseOrder(current.getId(), new QuoteService.ConvertRequest(
                created.getSupplier().getId(), ShippingCarrier.UPS, "1Z", null, null));

        ApiException error = assertThrows(ApiException.class, () -> service.updateView(
                current.getId(), request("0", line("79001-1", "40.00"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void deleteDraftRemovesQuote() {
        service.createView(request("0", line("79001-1", "50.00")));
        service.delete(current.getId());
        verify(quotes).delete(current);
    }

    @Test
    void cannotDeleteConvertedQuote() {
        service.createView(request("0", line("79001-1", "50.00")));
        PurchaseOrder created = PurchaseOrder.create(Supplier.create("Brick Depot"), 3);
        when(purchaseOrders.create(any())).thenReturn(created);
        service.convertToPurchaseOrder(current.getId(), new QuoteService.ConvertRequest(
                created.getSupplier().getId(), ShippingCarrier.UPS, "1Z", null, null));

        assertThrows(ApiException.class, () -> service.delete(current.getId()));
        verify(quotes, never()).delete(any());
    }

    private QuoteService.UpsertRequest request(String shipping, QuoteService.LineRequest... lines) {
        return new QuoteService.UpsertRequest(new BigDecimal(shipping), null, List.of(lines));
    }

    private QuoteService.LineRequest line(String setNumber, String cost) {
        return new QuoteService.LineRequest(null, setNumber, "Title " + setNumber, new BigDecimal(cost));
    }

    private CatalogItem catalog(String setNumber) {
        CatalogItem item = CatalogItem.create(setNumber);
        item.setName("Set " + setNumber);
        item.setTheme("Star Wars");
        return item;
    }

    private MarketSnapshot snapshot(CatalogItem catalog, BigDecimal median, int count) {
        MarketSnapshot snapshot = MarketSnapshot.create(catalog, Platform.EBAY, "NEW", ScanTrigger.MANUAL);
        snapshot.setMedianPrice(median);
        snapshot.setListingCount(count);
        return snapshot;
    }
}
