package com.ntropy.diagnosis.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.port.account.FinancialPosition;
import com.ntropy.diagnosis.port.account.FinancialPositionPort;
import com.ntropy.diagnosis.port.account.MonthlyExpense;
import com.ntropy.diagnosis.port.account.MonthlyExpensePort;
import com.ntropy.diagnosis.port.work.MonthlyIncomeAnalysis;
import com.ntropy.diagnosis.port.work.IncomeAnalysisPort;
import com.ntropy.diagnosis.service.DiagnosisResultService;

class LocalDiagnosisCommandClientTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;

    @Test
    void currentMonth_repeatedRecalculateKeepsFinalizedAtNull() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        client.recalculate(USER_ID, YearMonth.of(2026, 7));
        client.recalculate(USER_ID, YearMonth.of(2026, 7));

        DiagnosisResult stored = mapper.findByUserIdAndYearMonth(USER_ID, "2026-07");
        assertNotNull(stored);
        assertNull(stored.getFinalizedAt());
        assertTrue(fixtures.financialPosition.currentCalled);
    }

    @Test
    void pastMonth_firstCalculationUsesMonthEndAsOfAndFinalizesImmediately() {
        Clock clock = fixedClock(2026, 8, 1);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        client.recalculate(USER_ID, YearMonth.of(2026, 7));

        DiagnosisResult stored = mapper.findByUserIdAndYearMonth(USER_ID, "2026-07");
        assertNotNull(stored);
        assertNotNull(stored.getFinalizedAt());
        assertEquals(LocalDate.of(2026, 7, 31), fixtures.financialPosition.lastAsOf);
        assertTrue(fixtures.financialPosition.asOfCalled);
        assertFalse(fixtures.financialPosition.currentCalled);
    }

    @Test
    void pastMonth_provisionalSnapshotGetsFinalizedOnNextCall() {
        Clock clock = fixedClock(2026, 8, 1);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        // 확정되지 않은 임시 스냅샷이 이미 있는 상태를 흉내낸다(현재월이었을 때 저장된 행).
        mapper.upsert(diagnosisResult("2026-07", null));
        Fixtures fixtures = new Fixtures();
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        client.recalculate(USER_ID, YearMonth.of(2026, 7));

        DiagnosisResult stored = mapper.findByUserIdAndYearMonth(USER_ID, "2026-07");
        assertNotNull(stored.getFinalizedAt());
    }

    @Test
    void pastMonth_alreadyFinalized_skipsWithoutCallingAnyQueryClient() {
        Clock clock = fixedClock(2026, 8, 5);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        DiagnosisResult finalized = diagnosisResult("2026-07", LocalDateTime.of(2026, 8, 1, 0, 5));
        mapper.upsert(finalized);
        Fixtures fixtures = new Fixtures();
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        client.recalculate(USER_ID, YearMonth.of(2026, 7));

        assertFalse(fixtures.income.called);
        assertFalse(fixtures.expense.called);
        assertFalse(fixtures.financialPosition.currentCalled);
        assertFalse(fixtures.financialPosition.asOfCalled);
        DiagnosisResult stored = mapper.findByUserIdAndYearMonth(USER_ID, "2026-07");
        assertEquals(finalized.getFinalizedAt(), stored.getFinalizedAt());
    }

    @Test
    void futureYearMonth_throwsInvalidRequestWithoutCallingAnyQueryClient() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.recalculate(USER_ID, YearMonth.of(2026, 8))
        );

        assertEquals(DiagnosisErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
        assertFalse(fixtures.income.called);
        assertFalse(fixtures.expense.called);
    }

    @Test
    void incomeQueryFailure_propagatesAndDoesNotUpsert() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        fixtures.income.toThrow = new RuntimeException("work-service 장애");
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        assertThrows(RuntimeException.class, () -> client.recalculate(USER_ID, YearMonth.of(2026, 7)));

        assertNull(mapper.findByUserIdAndYearMonth(USER_ID, "2026-07"));
    }

    @Test
    void expenseQueryFailure_propagatesAndDoesNotUpsert() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        fixtures.expense.toThrow = new RuntimeException("account-service 장애");
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        assertThrows(RuntimeException.class, () -> client.recalculate(USER_ID, YearMonth.of(2026, 7)));

        assertNull(mapper.findByUserIdAndYearMonth(USER_ID, "2026-07"));
    }

    @Test
    void financialPositionQueryFailure_propagatesAndDoesNotUpsert() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        fixtures.financialPosition.toThrow = new ServiceException(DiagnosisErrorCode.INVALID_CALCULATION_INPUT, "잔액 오류");
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        assertThrows(ServiceException.class, () -> client.recalculate(USER_ID, YearMonth.of(2026, 7)));

        assertNull(mapper.findByUserIdAndYearMonth(USER_ID, "2026-07"));
    }

    @Test
    void nullFinancialPositionResult_throwsInvalidCalculationInputAndDoesNotUpsert() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        fixtures.financialPosition.summary = null;
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.recalculate(USER_ID, YearMonth.of(2026, 7))
        );

        assertEquals(DiagnosisErrorCode.INVALID_CALCULATION_INPUT.getStatusCode(), exception.getStatusCode());
        assertNull(mapper.findByUserIdAndYearMonth(USER_ID, "2026-07"));
    }

    @Test
    void nullIncomeAnalysisResult_throwsInvalidCalculationInputAndDoesNotUpsert() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        fixtures.income.returnNull = true;
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.recalculate(USER_ID, YearMonth.of(2026, 7))
        );

        assertEquals(DiagnosisErrorCode.INVALID_CALCULATION_INPUT.getStatusCode(), exception.getStatusCode());
        assertNull(mapper.findByUserIdAndYearMonth(USER_ID, "2026-07"));
    }

    @Test
    void nullMonthlyExpenseResult_throwsInvalidCalculationInputAndDoesNotUpsert() {
        Clock clock = fixedClock(2026, 7, 15);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        fixtures.expense.returnNull = true;
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.recalculate(USER_ID, YearMonth.of(2026, 7))
        );

        assertEquals(DiagnosisErrorCode.INVALID_CALCULATION_INPUT.getStatusCode(), exception.getStatusCode());
        assertNull(mapper.findByUserIdAndYearMonth(USER_ID, "2026-07"));
    }

    /**
     * UTC 기준으론 8/31이지만 서울 기준으론 9/1인 시각을 고정 Clock으로 주입한다.
     * 타임존을 잘못 적용했으면 8월을 "현재 월"로 오판해 임시 스냅샷 경로(finalize=false)를
     * 타게 되므로, 이 테스트는 실제로 과거월 확정 경로(월말 asOf, finalize=true)를
     * 타는지로 KST 적용 여부를 검증한다.
     */
    @Test
    void usesAsiaSeoulClockAtUtcMonthBoundary() {
        Instant utcAug31Evening = LocalDate.of(2026, 8, 31)
                .atTime(15, 30)
                .atZone(ZoneId.of("UTC"))
                .toInstant();
        Clock clock = Clock.fixed(utcAug31Evening, SEOUL);
        InMemoryDiagnosisResultMapper mapper = new InMemoryDiagnosisResultMapper();
        Fixtures fixtures = new Fixtures();
        LocalDiagnosisCommandClient client = clientWith(mapper, fixtures, clock);

        client.recalculate(USER_ID, YearMonth.of(2026, 8));

        DiagnosisResult stored = mapper.findByUserIdAndYearMonth(USER_ID, "2026-08");
        assertNotNull(stored.getFinalizedAt());
        assertEquals(LocalDate.of(2026, 8, 31), fixtures.financialPosition.lastAsOf);
    }

    private static Clock fixedClock(int year, int month, int day) {
        return Clock.fixed(
                LocalDate.of(year, month, day).atStartOfDay(SEOUL).toInstant(),
                SEOUL
        );
    }

    private static LocalDiagnosisCommandClient clientWith(
            InMemoryDiagnosisResultMapper mapper, Fixtures fixtures, Clock clock
    ) {
        return new LocalDiagnosisCommandClient(
                new DiagnosisResultService(mapper),
                fixtures.income,
                fixtures.expense,
                fixtures.financialPosition,
                clock
        );
    }

    private static DiagnosisResult diagnosisResult(String yearMonth, LocalDateTime finalizedAt) {
        return new DiagnosisResult(
                null,
                USER_ID,
                yearMonth,
                0L,
                0L,
                0L,
                0L,
                0L,
                null,
                0L,
                0L,
                0L,
                null,
                finalizedAt,
                null,
                null
        );
    }

    /** 세 조회 클라이언트를 한데 묶어 각 테스트에서 편하게 구성·검사하기 위한 묶음입니다. */
    private static class Fixtures {
        StubIncomeAnalysisQueryClient income = new StubIncomeAnalysisQueryClient();
        StubMonthlyExpenseQueryClient expense = new StubMonthlyExpenseQueryClient();
        StubFinancialPositionQueryClient financialPosition = new StubFinancialPositionQueryClient();
    }

    private static class StubIncomeAnalysisQueryClient implements IncomeAnalysisPort {
        boolean called;
        boolean returnNull;
        RuntimeException toThrow;
        Long totalIncome = 0L;
        Long unmatchedIncome = 0L;

        @Override
        public MonthlyIncomeAnalysis getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth) {
            called = true;
            if (toThrow != null) {
                throw toThrow;
            }
            if (returnNull) {
                return null;
            }
            return new MonthlyIncomeAnalysis(totalIncome, unmatchedIncome);
        }
    }

    private static class StubMonthlyExpenseQueryClient implements MonthlyExpensePort {
        boolean called;
        boolean returnNull;
        RuntimeException toThrow;
        Long totalExpense = 0L;
        Long fixedExpense = 0L;

        @Override
        public MonthlyExpense findMonthlyExpense(Long userId, String yearMonth) {
            called = true;
            if (toThrow != null) {
                throw toThrow;
            }
            if (returnNull) {
                return null;
            }
            return new MonthlyExpense(totalExpense, fixedExpense);
        }
    }

    private static class StubFinancialPositionQueryClient implements FinancialPositionPort {
        boolean currentCalled;
        boolean asOfCalled;
        LocalDate lastAsOf;
        RuntimeException toThrow;
        FinancialPosition summary = new FinancialPosition(0L, 0L, 0L);

        @Override
        public FinancialPosition findFinancialPosition(Long userId) {
            currentCalled = true;
            if (toThrow != null) {
                throw toThrow;
            }
            return summary;
        }

        @Override
        public FinancialPosition findFinancialPosition(Long userId, LocalDate asOf) {
            asOfCalled = true;
            lastAsOf = asOf;
            if (toThrow != null) {
                throw toThrow;
            }
            return summary;
        }
    }

    /** 실제 DB 대신 Map을 사용하는 테스트용 Mapper 구현체입니다. */
    private static class InMemoryDiagnosisResultMapper implements DiagnosisResultMapper {

        private final Map<String, DiagnosisResult> storage = new HashMap<>();

        @Override
        public int upsert(DiagnosisResult diagnosisResult) {
            String key = diagnosisResult.getUserId() + "-" + diagnosisResult.getYearMonth();
            DiagnosisResult existing = storage.get(key);
            if (existing != null && existing.getFinalizedAt() != null) {
                // 실제 조건부 upsert SQL과 동일하게, 이미 확정된 행은 보호한다.
                return 1;
            }
            storage.put(key, diagnosisResult);
            return 1;
        }

        @Override
        public DiagnosisResult findByUserIdAndYearMonth(Long userId, String yearMonth) {
            return storage.get(userId + "-" + yearMonth);
        }

        @Override
        public List<DiagnosisResult> findLatestByUserId(Long userId, int limit) {
            return storage.values().stream()
                    .filter(result -> result.getUserId().equals(userId))
                    .sorted(Comparator.comparing(DiagnosisResult::getYearMonth).reversed())
                    .limit(limit)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }
}
