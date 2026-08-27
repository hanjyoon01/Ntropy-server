package com.ntropy.user.api.dto;

/** 재발급된 액세스/리프레시 토큰 쌍. */
public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
