package com.ntropy.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** FastAPI 연결 주소를 공통 설정과 외부 설정에서 로드합니다. */
@Configuration
@PropertySources({
        @PropertySource(
                value = "classpath:fastapi.properties",
                ignoreResourceNotFound = true
        ),
        @PropertySource(
                value = "file:${NTROPY_CONFIG_DIR:./config}/fastapi.properties",
                ignoreResourceNotFound = true
        )
})
public class FastApiConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer fastApiPropertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    /** 소비 분류 FastAPI 배치를 제한된 동시성으로 실행한다. */
    @Bean("transactionClassificationExecutor")
    public ThreadPoolTaskExecutor transactionClassificationExecutor(
            @Value("${fastapi.classification.parallelism:4}") int configuredParallelism
    ) {
        int parallelism = Math.max(1, Math.min(configuredParallelism, 8));
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("txn-classification-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
