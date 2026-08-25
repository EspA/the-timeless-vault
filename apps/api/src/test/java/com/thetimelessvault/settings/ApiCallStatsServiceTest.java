package com.thetimelessvault.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiCallStatsServiceTest {

    @Mock ApiCallDailyRepository counts;
    @Mock PlatformTransactionManager transactions;

    ApiCallStatsService service;

    @BeforeEach
    void setUp() {
        service = new ApiCallStatsService(counts, transactions);
    }

    @Test
    void recordIncrementsTodayForThePlatform() {
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        service.record(ApiCallStatsService.SHOPIFY);

        verify(counts).increment(service.today(), ApiCallStatsService.SHOPIFY);
        verify(transactions).commit(any());
    }

    @Test
    void recordSwallowsIncrementFailures() {
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        doThrow(new RuntimeException("db down")).when(counts).increment(any(), any());

        assertDoesNotThrow(() -> service.record(ApiCallStatsService.EBAY));
    }

    @Test
    void snapshotFillsTodayAndSumsPlatforms() {
        LocalDate today = service.today();
        when(counts.findByDayGreaterThanEqualOrderByDayDescPlatformAsc(today.minusDays(13)))
                .thenReturn(List.of(
                        ApiCallDaily.of(today, ApiCallStatsService.EBAY, 2),
                        ApiCallDaily.of(today, ApiCallStatsService.BRICKLINK, 5),
                        ApiCallDaily.of(today, ApiCallStatsService.SHOPIFY, 1)
                ));

        ApiCallStatsService.Snapshot snapshot = service.snapshot();

        assertEquals("America/New_York", snapshot.timeZone());
        assertEquals(today, snapshot.today().day());
        assertEquals(2, snapshot.today().ebay());
        assertEquals(5, snapshot.today().bricklink());
        assertEquals(1, snapshot.today().shopify());
        assertEquals(0, snapshot.today().brickeconomy());
        assertEquals(8, snapshot.today().total());
        assertEquals(14, snapshot.recent().size());
        assertEquals(today, snapshot.recent().getFirst().day());
        assertEquals(today.minusDays(13), snapshot.recent().getLast().day());
        assertEquals(0, snapshot.recent().getLast().total());
    }
}
