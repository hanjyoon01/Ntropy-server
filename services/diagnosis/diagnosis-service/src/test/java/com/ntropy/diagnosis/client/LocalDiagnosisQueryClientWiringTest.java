package com.ntropy.diagnosis.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.ntropy.diagnosis.api.client.DiagnosisQueryClient;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.service.DiagnosisResultService;

/**
 * com.ntropy.diagnosis 패키지를 컴포넌트 스캔했을 때 DiagnosisQueryClient가
 * defense-service의 ObjectProvider fallback(userId -&gt; null)이 아니라 실제
 * LocalDiagnosisQueryClient로 해석되는지 확인한다.
 * api 모듈의 RootConfig(@ComponentScan("com.ntropy"))와 같은 스캔 방식을 좁혀서 재현한다.
 */
class LocalDiagnosisQueryClientWiringTest {

    @Configuration
    @ComponentScan(
            basePackages = "com.ntropy.diagnosis",
            useDefaultFilters = false,
            includeFilters = @Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {LocalDiagnosisQueryClient.class, DiagnosisResultService.class}
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
    }

    @Test
    void resolvesRealClientInsteadOfFallback() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DiagnosisQueryClient client = context.getBean(DiagnosisQueryClient.class);

            assertNotNull(client);
            assertInstanceOf(LocalDiagnosisQueryClient.class, client);
        }
    }
}
