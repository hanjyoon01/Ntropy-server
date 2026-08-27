package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;

/**
 * 일반 singleton의 {@link InitializingBean} 콜백이 모두 끝난 뒤
 * {@link VirtualFinancialDataSeedInitializer}가 실행되는지 검증한다. account-service는 user-service에
 * 컴파일 의존성이 없으므로 실제 회원 초기화 빈 대신 같은 생명주기의 가짜 빈으로 순서만 검증한다.
 */
class VirtualFinancialDataSeedInitializerOrderingTest {

    @Test
    void runsAfterVirtualUserSeedInitializerCallback() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("virtual-test.enabled", "true")));
        context.register(TestConfig.class, VirtualFinancialDataSeedInitializer.class);
        context.refresh();

        try {
            @SuppressWarnings("unchecked")
            List<String> order = context.getBean("executionOrder", List.class);
            assertEquals(List.of("virtualUserSeedInitializer", "financialDataSeedInitializer"), order);
        } finally {
            context.close();
        }
    }

    @Test
    void disabledAccountContextStartsWithoutVirtualUserSeedInitializer() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("virtual-test.enabled", "false")));
        context.register(DisabledTestConfig.class, VirtualFinancialDataSeedInitializer.class);

        try {
            assertDoesNotThrow(context::refresh);
            verify(context.getBean(VirtualFinancialDataService.class), never()).generate();
        } finally {
            context.close();
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        List<String> executionOrder() {
            return new ArrayList<>();
        }

        @Bean(name = "virtualUserSeedInitializer")
        InitializingBean virtualUserSeedInitializer(List<String> executionOrder) {
            return () -> executionOrder.add("virtualUserSeedInitializer");
        }

        @Bean
        VirtualFinancialDataService virtualFinancialDataService(List<String> executionOrder) {
            VirtualFinancialDataService service = mock(VirtualFinancialDataService.class);
            GenerationSummary summary = new GenerationSummary(
                    1, 2, 1, 300, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)
            );
            doAnswer(invocation -> {
                executionOrder.add("financialDataSeedInitializer");
                return summary;
            }).when(service).generate();
            return service;
        }
    }

    @Configuration
    static class DisabledTestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        VirtualFinancialDataService virtualFinancialDataService() {
            return mock(VirtualFinancialDataService.class);
        }
    }
}
