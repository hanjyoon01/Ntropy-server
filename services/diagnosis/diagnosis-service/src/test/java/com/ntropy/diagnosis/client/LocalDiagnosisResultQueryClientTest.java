package com.ntropy.diagnosis.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.api.dto.DiagnosisResultSummary;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.service.DiagnosisResultService;

class LocalDiagnosisResultQueryClientTest {

    @Test
    void mapsDiagnosisResultFieldsToSummary() {
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 7, 5, 9, 30);
        DiagnosisResult saved = new DiagnosisResult(
                1L, 10L, "2026-07",
                3_000_000L, 100_000L, 2_000_000L, 1_000_000L,
                600_000L, BigDecimal.valueOf(0.2),
                5_000_000L, 3_000_000L, 2_000_000L,
                calculatedAt, null, null, null
        );
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(saved);
        LocalDiagnosisResultQueryClient client =
                new LocalDiagnosisResultQueryClient(new DiagnosisResultService(mapper));

        DiagnosisResultSummary summary = client.findByUserIdAndYearMonth(10L, "2026-07");

        assertEquals(1L, summary.getDiagnosisId());
        assertEquals(10L, summary.getUserId());
        assertEquals("2026-07", summary.getYearMonth());
        assertEquals(3_000_000L, summary.getTotalIncome());
        assertEquals(100_000L, summary.getUnmatchedIncome());
        assertEquals(2_000_000L, summary.getTotalExpense());
        assertEquals(1_000_000L, summary.getNetCashFlow());
        assertEquals(600_000L, summary.getFixedExpense());
        assertEquals(BigDecimal.valueOf(0.2), summary.getFixedExpenseRatio());
        assertEquals(5_000_000L, summary.getTotalFinancialAssets());
        assertEquals(3_000_000L, summary.getLiquidAssets());
        assertEquals(2_000_000L, summary.getSafeAssets());
        assertEquals(calculatedAt, summary.getCalculatedAt());
    }

    @Test
    void throwsNotFoundWhenNoDiagnosisResultForMonth() {
        LocalDiagnosisResultQueryClient client =
                new LocalDiagnosisResultQueryClient(new DiagnosisResultService(new InMemoryMapper()));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.findByUserIdAndYearMonth(10L, "2026-07")
        );

        assertEquals(DiagnosisErrorCode.DIAGNOSIS_RESULT_NOT_FOUND.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void throwsInvalidRequestForMalformedYearMonth() {
        LocalDiagnosisResultQueryClient client =
                new LocalDiagnosisResultQueryClient(new DiagnosisResultService(new InMemoryMapper()));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.findByUserIdAndYearMonth(10L, "2026-13")
        );

        assertEquals(DiagnosisErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
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
