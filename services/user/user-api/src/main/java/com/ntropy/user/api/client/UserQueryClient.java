package com.ntropy.user.api.client;

import com.ntropy.user.api.dto.UserSummary;

/** 회원 도메인이 제공할 회원 조회 계약. */
public interface UserQueryClient {

    UserSummary getUserSummary(Long userId);
}
