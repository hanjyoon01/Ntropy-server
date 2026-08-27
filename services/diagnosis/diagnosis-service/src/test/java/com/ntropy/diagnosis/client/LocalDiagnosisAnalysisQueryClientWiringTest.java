package com.ntropy.diagnosis.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.ntropy.account.api.client.MonthlyExpenseQueryClient;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.diagnosis.api.client.DiagnosisAnalysisQueryClient;
import com.ntropy.diagnosis.api.dto.DiagnosisAnalysisSummary;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.service.CategoryExpenseCalculator;
import com.ntropy.diagnosis.service.DiagnosisResultService;

/**
 * com.ntropy.diagnosis 패키지를 컴포넌트 스캔했을 때 DiagnosisAnalysisQueryClient가
 * 실제 LocalDiagnosisAnalysisQueryClient로 해석되는지 확인한다. account-service 조회
 * 계약은 이 패키지 밖이라 스텁 Bean으로 대신한다.
 */
class LocalDiagnosisAnalysisQueryClientWiringTest {

    @Configuration
    @ComponentScan(
            basePackages = "com.ntropy.diagnosis",
            useDefaultFilters = false,
            includeFilters = @Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            LocalDiagnosisAnalysisQueryClient.class,
                            DiagnosisResultService.class,
                            CategoryExpenseCalculator.class
                    }
            )
    )
    static class TestConfig {

        @Bean
        DiagnosisResultMapper diagnosisResultMapper() {
            DiagnosisResult stored = new DiagnosisResult(
                    1L, 1L, "2026-07",
                    3_000_000L, 0L, 2_000_000L, 1_000_000L,
                    600_000L, BigDecimal.valueOf(0.2),
                    5_000_000L, 3_000_000L, 2_000_000L,
                    LocalDateTime.of(2026, 7, 5, 9, 0), null, null, null
            );
            return new DiagnosisResultMapper() {
                @Override
                public int upsert(DiagnosisResult diagnosisResult) {
                    return 0;
                }

                @Override
                public DiagnosisResult findByUserIdAndYearMonth(Long userId, String yearMonth) {
                    return stored;
                }

                @Override
                public List<DiagnosisResult> findLatestByUserId(Long userId, int limit) {
                    return List.of(stored);
                }
            };
        }

        @Bean
        MonthlyExpenseQueryClient monthlyExpenseQueryClient() {
            return (userId, yearMonth) ->
                    new MonthlyExpenseSummary(userId, yearMonth, 2_000_000L, 600_000L, Map.of("FOOD", 2_000_000L));
        }
    }

    @Test
    void resolvesRealClientAndCombinesStoredAndLiveData() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DiagnosisAnalysisQueryClient client = context.getBean(DiagnosisAnalysisQueryClient.class);

            assertNotNull(client);
            assertInstanceOf(LocalDiagnosisAnalysisQueryClient.class, client);

            DiagnosisAnalysisSummary summary = client.getMonthlyAnalysis(1L, YearMonth.of(2026, 7));

            assertEquals(3_000_000L, summary.getTotalIncome());
            assertEquals(1, summary.getCategoryExpenses().size());
        }
    }
}
