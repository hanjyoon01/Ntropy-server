package com.ntropy.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** account-service의 일일 금융거래 동기화 스케줄을 활성화한다. */
@Configuration
@EnableScheduling
public class DailyFinancialSyncSchedulingConfig {
}
