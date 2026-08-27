package com.ntropy.work.port.user;

import java.util.List;

import com.ntropy.common.domain.UserScope;

/**
 * work-service가 정의한, user-service의 활성 사용자 조회 포트.
 * UserScope는 특정 서비스의 내부 DTO가 아니라 배치 대상 범위를 나타내는 시스템 공통 어휘
 * (common.domain)이므로 그대로 재사용한다 - user-service 구현이 바뀌어도 이 값의 의미는
 * 바뀌지 않는다.
 */
public interface UserPort {

    List<Long> findActiveUserIds(UserScope scope);
}
