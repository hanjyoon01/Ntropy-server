package com.ntropy.diagnosis.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.client.MonthlyExpenseQueryClient;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.api.dto.CategoryExpenseSummary;
import com.ntropy.diagnosis.api.dto.DiagnosisAnalysisSummary;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.service.CategoryExpenseCalculator;
import com.ntropy.diagnosis.service.DiagnosisResultService;

class LocalDiagnosisAnalysisQueryClientTest {

    private static final Long USER_ID = 10L;

    @Test
    void combinesStoredScalarsWithLiveCategoryBreakdown() {
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 7, 5, 9, 30);
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(diagnosisResult(calculatedAt, null));
        StubMonthlyExpenseQueryClient expense = new StubMonthlyExpenseQueryClient();
        expense.totalExpense = 1_000_000L;
        expense.categoryExpenses = Map.of("FOOD", 600_000L, "TRANSPORTATION", 400_000L);
        LocalDiagnosisAnalysisQueryClient client = clientWith(mapper, expense);

        DiagnosisAnalysisSummary summary = client.getMonthlyAnalysis(USER_ID, YearMonth.of(2026, 7));

        assertEquals(3_000_000L, summary.getTotalIncome());
        assertEquals(2_000_000L, summary.getTotalExpense());
        assertEquals(600_000L, summary.getFixedExpense());
        assertEquals(calculatedAt, summary.getCalculatedAt());

        List<CategoryExpenseSummary> categories = summary.getCategoryExpenses();
        assertEquals(2, categories.size());
        CategoryExpenseSummary food = categories.stream()
                .filter(c -> c.getCategory().equals("FOOD")).findFirst().orElseThrow();
        assertEquals(600_000L, food.getAmount());
        assertEquals(BigDecimal.valueOf(0.6).setScale(4), food.getRatio());
    }

    @Test
    void reportsFinalizedTrueAndFinalizedAtWhenMonthIsFinalized() {
        LocalDateTime finalizedAt = LocalDateTime.of(2026, 8, 1, 0, 5);
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(diagnosisResult(LocalDateTime.of(2026, 7, 31, 23, 0), finalizedAt));
        LocalDiagnosisAnalysisQueryClient client = clientWith(mapper, new StubMonthlyExpenseQueryClient());

        DiagnosisAnalysisSummary summary = client.getMonthlyAnalysis(USER_ID, YearMonth.of(2026, 7));

        assertTrue(summary.isFinalized());
        assertEquals(finalizedAt, summary.getFinalizedAt());
    }

    @Test
    void reportsFinalizedFalseForProvisionalCurrentMonth() {
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(diagnosisResult(LocalDateTime.of(2026, 7, 15, 9, 0), null));
        LocalDiagnosisAnalysisQueryClient client = clientWith(mapper, new StubMonthlyExpenseQueryClient());

        DiagnosisAnalysisSummary summary = client.getMonthlyAnalysis(USER_ID, YearMonth.of(2026, 7));

        assertFalse(summary.isFinalized());
        assertNull(summary.getFinalizedAt());
    }

    @Test
    void throwsNotFoundWhenNoDiagnosisResultForMonth() {
        LocalDiagnosisAnalysisQueryClient client =
                clientWith(new InMemoryMapper(), new StubMonthlyExpenseQueryClient());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.getMonthlyAnalysis(USER_ID, YearMonth.of(2026, 7))
        );

        assertEquals(DiagnosisErrorCode.DIAGNOSIS_RESULT_NOT_FOUND.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void propagatesMonthlyExpenseQueryFailure() {
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(diagnosisResult(LocalDateTime.of(2026, 7, 15, 9, 0), null));
        StubMonthlyExpenseQueryClient expense = new StubMonthlyExpenseQueryClient();
        expense.toThrow = new RuntimeException("account-service 장애");
        LocalDiagnosisAnalysisQueryClient client = clientWith(mapper, expense);

        assertThrows(RuntimeException.class, () -> client.getMonthlyAnalysis(USER_ID, YearMonth.of(2026, 7)));
    }

    @Test
    void nullMonthlyExpenseResult_throwsInvalidCalculationInput() {
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(diagnosisResult(LocalDateTime.of(2026, 7, 15, 9, 0), null));
        StubMonthlyExpenseQueryClient expense = new StubMonthlyExpenseQueryClient();
        expense.returnNull = true;
        LocalDiagnosisAnalysisQueryClient client = clientWith(mapper, expense);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.getMonthlyAnalysis(USER_ID, YearMonth.of(2026, 7))
        );

        assertEquals(DiagnosisErrorCode.INVALID_CALCULATION_INPUT.getStatusCode(), exception.getStatusCode());
    }

    private static LocalDiagnosisAnalysisQueryClient clientWith(
            InMemoryMapper mapper, MonthlyExpenseQueryClient expenseQueryClient
    ) {
        return new LocalDiagnosisAnalysisQueryClient(
                new DiagnosisResultService(mapper),
                expenseQueryClient,
                new CategoryExpenseCalculator()
        );
    }

    private static DiagnosisResult diagnosisResult(LocalDateTime calculatedAt, LocalDateTime finalizedAt) {
        return new DiagnosisResult(
                1L,
                USER_ID,
                "2026-07",
                3_000_000L,
                100_000L,
                2_000_000L,
                1_000_000L,
                600_000L,
                BigDecimal.valueOf(0.2),
                5_000_000L,
                3_000_000L,
                2_000_000L,
                calculatedAt,
                finalizedAt,
                null,
                null
        );
    }

    private static class StubMonthlyExpenseQueryClient implements MonthlyExpenseQueryClient {
        RuntimeException toThrow;
        boolean returnNull;
        Long totalExpense = 0L;
        Long fixedExpense = 0L;
        Map<String, Long> categoryExpenses = Map.of();

        @Override
        public MonthlyExpenseSummary findMonthlyExpense(Long userId, String yearMonth) {
            if (toThrow != null) {
                throw toThrow;
            }
            if (returnNull) {
                return null;
            }
            return new MonthlyExpenseSummary(userId, yearMonth, totalExpense, fixedExpense, categoryExpenses);
        }
    }

    /** 실제 DB 대신 Map을 사용하는 테스트용 Mapper 구현체입니다. */
    private static class InMemoryMapper implements DiagnosisResultMapper {

        private final Map<String, DiagnosisResult> storage = new HashMap<>();

        void save(DiagnosisResult result) {
            storage.put(result.getUserId() + "-" + result.getYearMonth(), result);
        }

        @Override
        public int upsert(DiagnosisResult diagnosisResult) {
            save(diagnosisResult);
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
