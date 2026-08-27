package com.ntropy.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * WebPushSender의 @Async 메서드를 실행하기 위한 설정.
 * 다른 모듈에도 같은 이름의 설정이 생길 수 있어 모듈 접두사를 붙였다 (WorkSchedulingConfig와 동일한 이유).
 */
@Configuration
@EnableAsync
public class NotificationAsyncConfig {
}
