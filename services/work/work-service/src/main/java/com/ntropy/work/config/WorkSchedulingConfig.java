package com.ntropy.work.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * work-service에서 @Scheduled 어노테이션을 사용할 수 있도록
 * Spring 스케줄링 기능을 활성화하는 설정 클래스입니다.
 *
 * ai-service에도 동일 목적의 SchedulingConfig가 있는데, api 모듈이 전체 서비스를
 * 하나의 Spring 컨텍스트로 합치다 보니 클래스명이 같으면 기본 빈 이름(schedulingConfig)도
 * 겹쳐서 ConflictingBeanDefinitionException이 난다 - 그래서 모듈 접두사를 붙였다.
 */
@Configuration
@EnableScheduling
public class WorkSchedulingConfig {
}
