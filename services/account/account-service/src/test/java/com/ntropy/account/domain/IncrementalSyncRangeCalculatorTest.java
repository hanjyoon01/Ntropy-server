package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ntropy.account.config.IncrementalSyncPolicy;

class IncrementalSyncRangeCalculatorTest {

    private static final IncrementalSyncPolicy POLICY = new IncrementalSyncPolicy(1, 90);

    @Test
    void usesWatermarkMinusSafeOverlapWhenWatermarkExists() {
        LocalDateTime lastSuccessfulSyncedAt = LocalDateTime.of(2026, 8, 10, 3, 0);
        LocalDate businessDate = LocalDate.of(2026, 8, 14);

        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                lastSuccessfulSyncedAt, null, businessDate, POLICY
        );

        assertEquals(LocalDate.of(2026, 8, 9), startDate);
    }

    @Test
    void backfillsFromMostRecentStoredTransactionWhenNoWatermarkAndWithinLookback() {
        LocalDate mostRecentStoredTransactionDate = LocalDate.of(2026, 8, 5);
        LocalDate businessDate = LocalDate.of(2026, 8, 14);

        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                null, mostRecentStoredTransactionDate, businessDate, POLICY
        );

        assertEquals(LocalDate.of(2026, 8, 4), startDate);
    }

    @Test
    void clampsToMaxInitialLookbackWhenMostRecentStoredTransactionIsOlderThanLookback() {
        LocalDate mostRecentStoredTransactionDate = LocalDate.of(2020, 1, 1);
        LocalDate businessDate = LocalDate.of(2026, 8, 14);

        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                null, mostRecentStoredTransactionDate, businessDate, POLICY
        );

        assertEquals(businessDate.minusDays(90), startDate);
    }

    @Test
    void usesMaxInitialLookbackWhenNoWatermarkAndNoStoredTransaction() {
        LocalDate businessDate = LocalDate.of(2026, 8, 14);

        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(null, null, businessDate, POLICY);

        assertEquals(businessDate.minusDays(90), startDate);
    }

    @Test
    void watermarkTakesPrecedenceOverMostRecentStoredTransaction() {
        LocalDateTime lastSuccessfulSyncedAt = LocalDateTime.of(2026, 8, 12, 1, 0);
        LocalDate mostRecentStoredTransactionDate = LocalDate.of(2020, 1, 1);
        LocalDate businessDate = LocalDate.of(2026, 8, 14);

        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                lastSuccessfulSyncedAt, mostRecentStoredTransactionDate, businessDate, POLICY
        );

        assertEquals(LocalDate.of(2026, 8, 11), startDate);
    }
}
