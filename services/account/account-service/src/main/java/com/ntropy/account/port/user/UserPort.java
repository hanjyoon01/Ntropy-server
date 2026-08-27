package com.ntropy.account.port.user;

import java.util.List;

import com.ntropy.common.domain.UserScope;

/** account-service가 정의한, user-service의 회원 조회 포트. */
public interface UserPort {

    List<Long> findActiveUserIds(UserScope scope);

    SeededVirtualUserBatch findSeededVirtualUsers();
}
