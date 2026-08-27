package com.ntropy.account.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AccountClassificationAsyncConfigTest {

    private final AccountClassificationAsyncConfig config =
            new AccountClassificationAsyncConfig();

    @Test
    void usesConfiguredPoolAndQueueSizes() {
        ThreadPoolTaskExecutor executor =
                config.accountClassificationJobExecutor(2, 4, 25);
        executor.initialize();

        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        assertEquals(25, executor.getQueueCapacity());
        assertInstanceOf(
                ThreadPoolExecutor.AbortPolicy.class,
                executor.getThreadPoolExecutor().getRejectedExecutionHandler()
        );

        executor.shutdown();
    }

    @Test
    void clampsUnsafeConfigurationValues() {
        ThreadPoolTaskExecutor executor =
                config.accountClassificationJobExecutor(0, 99, 0);
        executor.initialize();

        assertEquals(1, executor.getCorePoolSize());
        assertEquals(8, executor.getMaxPoolSize());
        assertEquals(1, executor.getQueueCapacity());

        executor.shutdown();
    }
}
