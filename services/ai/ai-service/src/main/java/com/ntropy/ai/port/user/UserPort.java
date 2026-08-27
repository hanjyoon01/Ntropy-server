package com.ntropy.ai.port.user;

import java.util.List;

import com.ntropy.common.domain.UserScope;

/**
 * ai-service가 정의한, user-service의 회원 조회 포트.
 * UserScope는 특정 서비스의 내부 DTO가 아니라 배치 대상 범위를 나타내는 시스템 공통 어휘
 * (common.domain)이므로 그대로 재사용한다.
 */
public interface UserPort {

    List<Long> findActiveUserIds(UserScope scope);

    AiUser findUser(Long userId);
}
