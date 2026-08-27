package com.ntropy.ai.port.payment;

import com.ntropy.common.domain.Feature;

/**
 * ai-service가 정의한, payment-service의 구독 기능 지원 여부 확인 포트.
 * Feature는 특정 서비스의 내부 DTO가 아니라 구독으로 잠기는 기능을 나타내는 시스템 공통 어휘
 * (common.domain)이므로 그대로 재사용한다.
 */
@FunctionalInterface
public interface SubscriptionPort {

    boolean supportsFeature(Long userId, Feature feature);
}
