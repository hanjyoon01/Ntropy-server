package com.ntropy.user.api.client;

import com.ntropy.user.api.dto.VerifiedToken;

/**
 * 회원 도메인이 제공할 액세스 토큰 검증 계약.
 * api 모듈의 인증 필터가 회원 도메인 내부 구현을 알지 않고 토큰을 검증하기 위해 사용한다.
 */
public interface TokenVerifier {

    /** 유효하지 않거나 만료된 토큰이면 {@code null}을 반환한다. */
    VerifiedToken verify(String accessToken);
}
