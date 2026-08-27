package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.entity.AccountTransaction;

class NtropyIncrementalTransactionGeneratorTest {

    private static final Long USER_ID = 9_000_046_101L;
    private static final Long ACCOUNT_ID = 555L;

    private final NtropyIncrementalTransactionGenerator generator = new NtropyIncrementalTransactionGenerator();

    @Test
    void producesNoTransactionsAfterTheEndDate() {
        LocalDate endDate = LocalDate.of(2026, 8, 14);

        List<AccountTransaction> transactions = generator.generate(
                USER_ID, ACCOUNT_ID, endDate.minusDays(5), endDate
        );

        assertTrue(transactions.stream().noneMatch(t -> t.getTranDate().isAfter(endDate)));
    }

    @Test
    void excludesTheStartDateItself() {
        LocalDate startDate = LocalDate.of(2026, 8, 10);

        List<AccountTransaction> transactions = generator.generate(USER_ID, ACCOUNT_ID, startDate, startDate);

        assertTrue(transactions.isEmpty(), "startDate 당일은 (startDate, endDate] 구간에 포함되지 않아야 합니다");
    }

    @Test
    void isDeterministicAcrossRepeatedCallsForTheSameRange() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 14);

        List<AccountTransaction> first = generator.generate(USER_ID, ACCOUNT_ID, startDate, endDate);
        List<AccountTransaction> second = generator.generate(USER_ID, ACCOUNT_ID, startDate, endDate);

        assertEquals(
                first.stream().map(AccountTransaction::getFingerprint).toList(),
                second.stream().map(AccountTransaction::getFingerprint).toList()
        );
    }

    @Test
    void reRunningAnOverlappingWindowProducesTheSameFingerprintsForTheSharedDay() {
        LocalDate sharedDay = LocalDate.of(2026, 8, 13);
        LocalDate businessDate = LocalDate.of(2026, 8, 14);

        // 첫 실행: ...8/13, 8/14까지 생성
        List<AccountTransaction> firstRun = generator.generate(USER_ID, ACCOUNT_ID, sharedDay.minusDays(1), businessDate);
        // 두 번째 실행: 안전 중첩으로 8/13부터 다시 생성(8/13, 8/14 포함)
        List<AccountTransaction> secondRun = generator.generate(USER_ID, ACCOUNT_ID, sharedDay.minusDays(2), businessDate);

        Set<String> firstSharedDayFingerprints = fingerprintsOn(firstRun, sharedDay);
        Set<String> secondSharedDayFingerprints = fingerprintsOn(secondRun, sharedDay);

        assertFalse(firstSharedDayFingerprints.isEmpty());
        assertEquals(firstSharedDayFingerprints, secondSharedDayFingerprints);
    }

    @Test
    void allGeneratedFingerprintsAreUniqueWithinTheRange() {
        List<AccountTransaction> transactions = generator.generate(
                USER_ID, ACCOUNT_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 14)
        );

        long distinctFingerprints = transactions.stream().map(AccountTransaction::getFingerprint).distinct().count();
        assertEquals(transactions.size(), distinctFingerprints);
    }

    @Test
    void generatesAtLeastOneConsumptionTransactionPerDayInRange() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 5);

        List<AccountTransaction> transactions = generator.generate(USER_ID, ACCOUNT_ID, startDate, endDate);

        for (LocalDate day = startDate.plusDays(1); !day.isAfter(endDate); day = day.plusDays(1)) {
            LocalDate d = day;
            assertTrue(transactions.stream().anyMatch(t -> t.getTranDate().equals(d)),
                    "매일 최소 한 건의 소비 거래가 있어야 합니다: " + d);
        }
    }

    private static Set<String> fingerprintsOn(List<AccountTransaction> transactions, LocalDate date) {
        return transactions.stream()
                .filter(t -> t.getTranDate().equals(date))
                .map(AccountTransaction::getFingerprint)
                .collect(Collectors.toSet());
    }
}
