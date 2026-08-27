package com.ntropy.user.api.dto;

/** 소셜 로그인 성공 결과. 액세스/리프레시 토큰과 온보딩 진행 여부를 함께 전달한다. */
public record OAuthLoginResult(
        String accessToken,
        String refreshToken,
        Long userId,
        String name,
        String email,
        Boolean onboardingCompleted
) {
}
