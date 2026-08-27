package com.ntropy.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ai-service에서 @Scheduled 어노테이션을 사용할 수 있도록
 * Spring 스케줄링 기능을 활성화하는 설정 클래스입니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}