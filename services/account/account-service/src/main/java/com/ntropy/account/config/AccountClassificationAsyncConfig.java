package com.ntropy.account.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 계좌 연동 요청과 소비 분류 작업이 서로의 스레드 풀을 점유하지 않도록 분리합니다. */
@Configuration
@PropertySources({
        @PropertySource(
                value = "classpath:account-classification.properties",
                ignoreResourceNotFound = true
        ),
        @PropertySource(
                value = "file:${NTROPY_CONFIG_DIR:./config}/account-classification.properties",
                ignoreResourceNotFound = true
        )
})
public class AccountClassificationAsyncConfig {

    @Bean("accountClassificationJobExecutor")
    public ThreadPoolTaskExecutor accountClassificationJobExecutor(
            @Value("${account.classification.async.core-pool-size:1}") int configuredCorePoolSize,
            @Value("${account.classification.async.max-pool-size:2}") int configuredMaxPoolSize,
            @Value("${account.classification.async.queue-capacity:100}") int configuredQueueCapacity
    ) {
        int corePoolSize = Math.max(1, Math.min(configuredCorePoolSize, 8));
        int maxPoolSize = Math.max(corePoolSize, Math.min(configuredMaxPoolSize, 8));
        int queueCapacity = Math.max(1, Math.min(configuredQueueCapacity, 1000));

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("account-classification-job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
