package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.ntropy.account.api.client.FinancialCommitmentQueryClient;
import com.ntropy.account.mapper.FinancialCommitmentMapper;
import com.ntropy.account.mapper.projection.InsuranceOutflowRow;
import com.ntropy.account.mapper.projection.LoanCommitmentCandidateRow;
import com.ntropy.account.mapper.projection.SavingCommitmentCandidateRow;
import com.ntropy.account.service.FinancialCommitmentService;

/**
 * com.ntropy.account 패키지를 컴포넌트 스캔했을 때 FinancialCommitmentQueryClient가
 * defense-service의 ObjectProvider fallback(빈 리스트)이 아니라 실제
 * LocalFinancialCommitmentQueryClient로 해석되는지 확인한다.
 * api 모듈의 RootConfig(@ComponentScan("com.ntropy"))와 같은 스캔 방식을 좁혀서 재현한다.
 */
class LocalFinancialCommitmentQueryClientWiringTest {

    @Configuration
    @ComponentScan(
            basePackages = "com.ntropy.account",
            useDefaultFilters = false,
            includeFilters = @Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {LocalFinancialCommitmentQueryClient.class, FinancialCommitmentService.class}
            )
    )
    static class TestConfig {

        @Bean
        FinancialCommitmentMapper financialCommitmentMapper() {
            return new FinancialCommitmentMapper() {
                @Override
                public List<SavingCommitmentCandidateRow> findSavingCommitmentCandidates(Long userId) {
                    return List.of();
                }

                @Override
                public List<LoanCommitmentCandidateRow> findLoanCommitmentCandidates(
                        Long userId, List<String> loanDisbursementKeywords) {
                    return List.of();
                }

                @Override
                public List<InsuranceOutflowRow> findInsuranceOutflowCandidates(
                        Long userId, LocalDate observationStartDate, LocalDate observationEndDate) {
                    return List.of();
                }
            };
        }

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }

    @Test
    void resolvesRealClientInsteadOfFallback() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            FinancialCommitmentQueryClient client = context.getBean(FinancialCommitmentQueryClient.class);

            assertNotNull(client);
            assertInstanceOf(LocalFinancialCommitmentQueryClient.class, client);
        }
    }
}
