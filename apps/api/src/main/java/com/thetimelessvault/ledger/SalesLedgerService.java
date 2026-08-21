package com.thetimelessvault.ledger;

import com.thetimelessvault.catalog.BrickEconomyClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SalesLedgerService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(3600);

    private final BrickEconomyClient brickEconomy;
    private final Object lock = new Object();
    private List<LedgerSale> cached = List.of();
    private Instant fetchedAt;

    public SalesLedgerService(BrickEconomyClient brickEconomy) {
        this.brickEconomy = brickEconomy;
    }

    public LedgerPage load(boolean refresh) {
        List<LedgerSale> items = sales(refresh);
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        for (LedgerSale sale : items) {
            revenue = revenue.add(sale.salePriceTotal() == null ? BigDecimal.ZERO : sale.salePriceTotal());
            profit = profit.add(sale.profit() == null ? BigDecimal.ZERO : sale.profit());
        }
        return new LedgerPage(
                items,
                items.size(),
                revenue.setScale(2, RoundingMode.HALF_UP),
                profit.setScale(2, RoundingMode.HALF_UP),
                fetchedAt
        );
    }

    private List<LedgerSale> sales(boolean refresh) {
        synchronized (lock) {
            if (!refresh && cached != null && fetchedAt != null
                    && fetchedAt.isAfter(Instant.now().minus(CACHE_TTL))) {
                return cached;
            }
            cached = List.copyOf(SalesLedgerMapper.from(brickEconomy.getSalesLedger()));
            fetchedAt = Instant.now();
            return cached;
        }
    }

    public record LedgerPage(
            List<LedgerSale> items,
            int total,
            BigDecimal revenue,
            BigDecimal profit,
            Instant fetchedAt
    ) {
    }
}
