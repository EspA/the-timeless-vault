package com.thetimelessvault.inbound;

import com.thetimelessvault.catalog.CatalogItem;
import com.thetimelessvault.catalog.CatalogService;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ItemCondition;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.market.MarketSnapshot;
import com.thetimelessvault.market.MarketSnapshotRepository;
import com.thetimelessvault.market.MarketStats;
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
public class QuoteService {

    private final QuoteRepository quotes;
    private final CatalogService catalogService;
    private final MarketSnapshotRepository snapshots;
    private final PurchaseOrderService purchaseOrders;

    public QuoteService(
            QuoteRepository quotes,
            CatalogService catalogService,
            MarketSnapshotRepository snapshots,
            PurchaseOrderService purchaseOrders
    ) {
        this.quotes = quotes;
        this.catalogService = catalogService;
        this.snapshots = snapshots;
        this.purchaseOrders = purchaseOrders;
    }

    @Transactional(readOnly = true)
    public QuoteDtos.QuotePage page(int page, int size) {
        int pageSize = Math.min(10_000, Math.max(1, size));
        int pageIndex = Math.max(0, page);
        Page<Quote> result = quotes.findAll(PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        List<Quote> rows = result.getContent();
        rows.forEach(quote -> quote.getLines().size());
        Map<UUID, BigDecimal> medians = mediansFor(rows.stream().flatMap(quote -> quote.getLines().stream()).toList());
        return new QuoteDtos.QuotePage(
                rows.stream().map(quote -> toView(quote, medians)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                Math.max(1, result.getTotalPages())
        );
    }

    @Transactional(readOnly = true)
    public QuoteDtos.QuoteView view(UUID id) {
        Quote quote = get(id);
        return toView(quote, mediansFor(quote.getLines()));
    }

    @Transactional
    public QuoteDtos.QuoteView createView(UpsertRequest request) {
        List<LineDraft> drafts = requireLines(request.lines());
        Quote quote = Quote.create((int) quotes.nextQuoteNumber());
        quote.setShippingTotal(QuoteMath.money(request.shippingTotal()));
        quote.setDescription(blankToNull(request.description()));
        applyLines(quote, drafts);
        return toView(quotes.save(quote));
    }

    @Transactional
    public QuoteDtos.QuoteView updateView(UUID id, UpsertRequest request) {
        Quote quote = get(id);
        requireDraft(quote);
        quote.setShippingTotal(QuoteMath.money(request.shippingTotal()));
        quote.setDescription(blankToNull(request.description()));
        syncLines(quote, requireLines(request.lines()));
        quote.touch();
        return toView(quotes.save(quote));
    }

    @Transactional
    public void delete(UUID id) {
        Quote quote = get(id);
        requireDraft(quote);
        quotes.delete(quote);
    }

    @Transactional
    public QuoteDtos.QuoteView convertToPurchaseOrder(UUID id, ConvertRequest request) {
        Quote quote = get(id);
        requireDraft(quote);
        if (quote.getLines().isEmpty()) {
            throw ApiException.badRequest("Add at least one quote line before creating a purchase order");
        }
        Map<UUID, BigDecimal> medians = mediansFor(quote.getLines());
        QuoteDtos.QuoteView computed = toView(quote, medians);
        List<PurchaseOrderService.LineRequest> poLines = new ArrayList<>();
        for (QuoteDtos.LineView line : computed.lines()) {
            if (line.costWithShipping() == null || line.costWithShipping().signum() <= 0) {
                throw ApiException.badRequest("Each quote line needs a cost with shipping greater than 0");
            }
            poLines.add(new PurchaseOrderService.LineRequest(
                    null,
                    line.setNumber(),
                    line.title(),
                    1,
                    line.costWithShipping()
            ));
        }
        PurchaseOrder created = purchaseOrders.create(new PurchaseOrderService.UpsertRequest(
                request.supplierId(),
                request.expectedArrival(),
                request.trackingNumber(),
                request.carrier(),
                request.note(),
                poLines
        ));
        quote.markConverted(created);
        return toView(quotes.save(quote), medians);
    }

    private Quote get(UUID id) {
        return quotes.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Quote not found"));
    }

    private QuoteDtos.QuoteView toView(Quote quote) {
        return toView(quote, mediansFor(quote.getLines()));
    }

    private QuoteDtos.QuoteView toView(Quote quote, Map<UUID, BigDecimal> medians) {
        List<QuoteLine> lines = quote.getLines();
        List<BigDecimal> costs = lines.stream().map(QuoteLine::getCost).toList();
        List<BigDecimal> prorated = QuoteMath.proratedShipping(costs, quote.getShippingTotal());
        BigDecimal totalCost = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalProrated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalMedian = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean anyMedian = false;
        BigDecimal totalMargin = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean anyMargin = false;
        List<QuoteDtos.LineView> views = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            QuoteLine line = lines.get(i);
            BigDecimal cost = QuoteMath.money(line.getCost());
            BigDecimal ship = prorated.get(i);
            BigDecimal landed = cost.add(ship);
            BigDecimal median = medians.get(line.getCatalogItemId());
            BigDecimal marginDollars = QuoteMath.marginDollars(median, landed);
            BigDecimal marginPercent = QuoteMath.marginPercent(marginDollars, landed);
            totalCost = totalCost.add(cost);
            totalProrated = totalProrated.add(ship);
            if (median != null) {
                totalMedian = totalMedian.add(QuoteMath.money(median));
                anyMedian = true;
            }
            if (marginDollars != null) {
                totalMargin = totalMargin.add(marginDollars);
                anyMargin = true;
            }
            views.add(new QuoteDtos.LineView(
                    line.getId(),
                    line.getCatalogItemId(),
                    line.getSetNumber(),
                    line.getTitle(),
                    cost,
                    ship,
                    landed,
                    median == null ? null : QuoteMath.money(median),
                    marginPercent,
                    marginDollars,
                    QuoteMath.marginTone(marginPercent)
            ));
        }
        BigDecimal shipping = QuoteMath.money(quote.getShippingTotal());
        BigDecimal totalLanded = totalCost.add(shipping);
        BigDecimal averageMargin = anyMargin ? QuoteMath.marginPercent(totalMargin, totalLanded) : null;
        PurchaseOrder order = quote.getPurchaseOrder();
        return new QuoteDtos.QuoteView(
                quote.getId(),
                quote.displayNumber(),
                quote.getStatus(),
                quote.getDescription(),
                lines.size(),
                shipping,
                totalCost,
                totalProrated,
                totalLanded,
                anyMedian ? totalMedian : null,
                averageMargin,
                QuoteMath.marginTone(averageMargin),
                anyMargin ? totalMargin : null,
                order == null ? null : order.getId(),
                order == null ? null : order.displayNumber(),
                views,
                quote.getCreatedAt(),
                quote.getUpdatedAt()
        );
    }

    private Map<UUID, BigDecimal> mediansFor(List<QuoteLine> lines) {
        Set<UUID> catalogIds = lines.stream()
                .map(QuoteLine::getCatalogItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (catalogIds.isEmpty()) {
            return Map.of();
        }
        List<MarketSnapshot> latest = snapshots.findLatestByCatalogItemIdIn(catalogIds);
        return MarketStats.combinedMedians(latest);
    }

    private void applyLines(Quote quote, List<LineDraft> drafts) {
        quote.getLines().clear();
        int position = 0;
        for (LineDraft draft : drafts) {
            quote.addLine(QuoteLine.create(
                    draft.catalogItemId(), draft.setNumber(), draft.title(), draft.cost(), position++));
        }
    }

    private void syncLines(Quote quote, List<LineDraft> drafts) {
        Map<UUID, QuoteLine> existing = quote.getLines().stream()
                .collect(Collectors.toMap(QuoteLine::getId, Function.identity()));
        Set<UUID> keep = new HashSet<>();
        int position = 0;
        for (LineDraft draft : drafts) {
            if (draft.id() == null) {
                QuoteLine created = QuoteLine.create(
                        draft.catalogItemId(), draft.setNumber(), draft.title(), draft.cost(), position++);
                quote.addLine(created);
                continue;
            }
            QuoteLine line = existing.get(draft.id());
            if (line == null) {
                throw ApiException.badRequest("Unknown quote line");
            }
            keep.add(line.getId());
            line.setCatalogItemId(draft.catalogItemId());
            line.setSetNumber(draft.setNumber());
            line.setTitle(draft.title());
            line.setCost(draft.cost());
            line.setPosition(position++);
            line.touch();
        }
        quote.getLines().removeIf(line -> existing.containsKey(line.getId()) && !keep.contains(line.getId()));
    }

    private List<LineDraft> requireLines(List<LineRequest> lines) {
        if (lines == null) {
            throw ApiException.badRequest("Add at least one quote line");
        }
        List<LineDraft> drafts = new ArrayList<>();
        for (LineRequest request : lines) {
            if (request == null || request.setNumber() == null || request.setNumber().isBlank()) {
                continue;
            }
            drafts.add(draft(request));
        }
        if (drafts.isEmpty()) {
            throw ApiException.badRequest("Add at least one quote line");
        }
        return drafts;
    }

    private LineDraft draft(LineRequest request) {
        CatalogItem catalog = catalogService.lookup(request.setNumber().trim(), false);
        String title = request.title() == null || request.title().isBlank()
                ? ThemeMapper.suggestedTitle(
                        catalog.getTheme(), catalog.getSubtheme(), catalog.getSetNumber(), catalog.getName(),
                        ItemCondition.NEW_SEALED)
                : ThemeMapper.limitTitle(request.title());
        BigDecimal cost = request.cost() == null ? BigDecimal.ZERO : request.cost();
        if (cost.signum() < 0) {
            throw ApiException.badRequest("Cost cannot be negative");
        }
        return new LineDraft(
                request.id(),
                catalog.getId(),
                catalog.getSetNumber(),
                title,
                QuoteMath.money(cost)
        );
    }

    private static void requireDraft(Quote quote) {
        if (!quote.isDraft()) {
            throw ApiException.badRequest("Converted quotes cannot be changed");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record LineRequest(UUID id, String setNumber, String title, BigDecimal cost) {
    }

    public record UpsertRequest(BigDecimal shippingTotal, String description, List<LineRequest> lines) {
    }

    public record ConvertRequest(
            UUID supplierId,
            ShippingCarrier carrier,
            String trackingNumber,
            LocalDate expectedArrival,
            String note
    ) {
    }

    private record LineDraft(UUID id, UUID catalogItemId, String setNumber, String title, BigDecimal cost) {
    }
}
