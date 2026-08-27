package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.ai.client.fastapi.FastApiProductRecommendationClient;
import com.ntropy.ai.config.AiReportBatchUserScopeProperties;
import com.ntropy.ai.domain.AiReport;
import com.ntropy.ai.dto.fastapi.ProductRecommendationResponse;
import com.ntropy.ai.mapper.AiReportMapper;
import com.ntropy.ai.port.user.AiUser;
import com.ntropy.ai.port.user.UserPort;
import com.ntropy.ai.port.work.IncomeAnalysisPort;
import com.ntropy.common.domain.UserScope;

class MonthlyAiReportTransactionIntegrationTest {

    private static final Long USER_ID = 7L;
    private static final YearMonth TARGET_MONTH = YearMonth.of(2026, 7);

    @Test
    void automaticDeliveryRunsAfterTransactionCommitWithNoActiveTransaction() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TransactionTestConfig.class)) {
            ControllableTransactionManager transactionManager =
                    context.getBean(ControllableTransactionManager.class);
            AiReportMapper mapper = context.getBean(AiReportMapper.class);
            AiReportEmailDeliveryService deliveryService = mock(AiReportEmailDeliveryService.class);

            doAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return 1;
            }).when(mapper).upsert(any(AiReport.class));
            doAnswer(invocation -> {
                assertTrue(transactionManager.commitCompleted);
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return null;
            }).when(deliveryService).deliverAutomatically(USER_ID, TARGET_MONTH.toString());

            orchestrationService(context.getBean(AiReportService.class), deliveryService)
                    .runBatch(TARGET_MONTH);

            assertTrue(transactionManager.commitAttempted);
            assertTrue(transactionManager.commitCompleted);
            verify(deliveryService).deliverAutomatically(USER_ID, TARGET_MONTH.toString());
        }
    }

    @Test
    void commitFailurePreventsAutomaticDelivery() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TransactionTestConfig.class)) {
            ControllableTransactionManager transactionManager =
                    context.getBean(ControllableTransactionManager.class);
            transactionManager.failCommit = true;
            AiReportEmailDeliveryService deliveryService = mock(AiReportEmailDeliveryService.class);

            orchestrationService(context.getBean(AiReportService.class), deliveryService)
                    .runBatch(TARGET_MONTH);

            assertTrue(transactionManager.commitAttempted);
            assertFalse(transactionManager.commitCompleted);
            verify(deliveryService, never()).deliverAutomatically(any(), any());
        }
    }

    private MonthlyAiReportOrchestrationService orchestrationService(
            AiReportService aiReportService,
            AiReportEmailDeliveryService deliveryService
    ) {
        IncomeAnalysisPort incomeClient = mock(IncomeAnalysisPort.class);
        when(incomeClient.getMonthlyIncomeAnalysisBulk(anyList(), eq(TARGET_MONTH)))
                .thenReturn(Map.of());
        FastApiProductRecommendationClient recommendationClient =
                mock(FastApiProductRecommendationClient.class);
        when(recommendationClient.recommend(any()))
                .thenReturn(new ProductRecommendationResponse());

        UserPort userPort = new UserPort() {
            @Override
            public java.util.List<Long> findActiveUserIds(UserScope scope) {
                return java.util.List.of(USER_ID);
            }

            @Override
            public AiUser findUser(Long userId) {
                throw new UnsupportedOperationException("이 테스트에서는 사용하지 않습니다");
            }
        };

        return new MonthlyAiReportOrchestrationService(
                userPort,
                new AiReportBatchUserScopeProperties("REAL_ONLY"),
                incomeClient,
                (userId, yearMonth) -> null,
                recommendationClient,
                aiReportService,
                deliveryService,
                new ObjectMapper()
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionTestConfig {

        @Bean
        ControllableTransactionManager transactionManager() {
            return new ControllableTransactionManager();
        }

        @Bean
        AiReportMapper aiReportMapper() {
            return mock(AiReportMapper.class);
        }

        @Bean
        AiReportService aiReportService(AiReportMapper aiReportMapper) {
            return new AiReportService(aiReportMapper);
        }
    }

    static class ControllableTransactionManager extends AbstractPlatformTransactionManager {

        private boolean failCommit;
        private boolean commitAttempted;
        private boolean commitCompleted;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 테스트 전용 논리 트랜잭션을 시작한다.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitAttempted = true;
            if (failCommit) {
                throw new TransactionSystemException("simulated commit failure");
            }
            commitCompleted = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // 운영 DB를 사용하지 않으므로 롤백할 외부 상태가 없다.
        }
    }
}
