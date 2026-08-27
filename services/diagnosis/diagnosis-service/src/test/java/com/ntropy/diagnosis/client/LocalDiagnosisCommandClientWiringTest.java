package com.ntropy.diagnosis.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.ntropy.diagnosis.api.client.DiagnosisCommandClient;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.port.account.FinancialPosition;
import com.ntropy.diagnosis.port.account.FinancialPositionPort;
import com.ntropy.diagnosis.port.account.MonthlyExpense;
import com.ntropy.diagnosis.port.account.MonthlyExpensePort;
import com.ntropy.diagnosis.port.work.MonthlyIncomeAnalysis;
import com.ntropy.diagnosis.port.work.IncomeAnalysisPort;
import com.ntropy.diagnosis.service.DiagnosisResultService;

/**
 * com.ntropy.diagnosis 패키지를 컴포넌트 스캔했을 때 DiagnosisCommandClient가
 * 실제 LocalDiagnosisCommandClient로 해석되는지 확인한다. work-service·account-service
 * 조회 계약은 이 패키지 밖이라 스텁 Bean으로 대신한다.
 */
class LocalDiagnosisCommandClientWiringTest {

    @Configuration
    @ComponentScan(
            basePackages = "com.ntropy.diagnosis",
            useDefaultFilters = false,
            includeFilters = @Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {LocalDiagnosisCommandClient.class, DiagnosisResultService.class}
            )
    )
    static class TestConfig {

        @Bean
        DiagnosisResultMapper diagnosisResultMapper() {
            return new DiagnosisResultMapper() {
                @Override
                public int upsert(DiagnosisResult diagnosisResult) {
                    return 0;
                }

                @Override
                public DiagnosisResult findByUserIdAndYearMonth(Long userId, String yearMonth) {
                    return null;
                }

                @Override
                public List<DiagnosisResult> findLatestByUserId(Long userId, int limit) {
                    return List.of();
                }
            };
        }

        @Bean
        IncomeAnalysisPort incomeAnalysisPort() {
            return (userId, yearMonth) -> new MonthlyIncomeAnalysis(0L, 0L);
        }

        @Bean
        MonthlyExpensePort monthlyExpensePort() {
            return (userId, yearMonth) -> new MonthlyExpense(0L, 0L);
        }

        @Bean
        FinancialPositionPort financialPositionPort() {
            return new FinancialPositionPort() {
                @Override
                public FinancialPosition findFinancialPosition(Long userId) {
                    return new FinancialPosition(0L, 0L, 0L);
                }

                @Override
                public FinancialPosition findFinancialPosition(Long userId, LocalDate asOf) {
                    return new FinancialPosition(0L, 0L, 0L);
                }
            };
        }
    }

    @Test
    void resolvesRealClientAndCanRecalculateCurrentMonth() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DiagnosisCommandClient client = context.getBean(DiagnosisCommandClient.class);

            assertNotNull(client);
            assertInstanceOf(LocalDiagnosisCommandClient.class, client);

            // 예외 없이 끝나면(반환형이 void이므로) 3개 조회 Client가 실제로 연결됐다는 뜻이다.
            client.recalculate(1L, YearMonth.now());
        }
    }
}
