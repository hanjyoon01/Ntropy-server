package com.ntropy.user.api.dto;

/** 검증에 성공한 액세스 토큰에서 추출한 인증 주체 정보. */
public record VerifiedToken(
        Long userId,
        String role
) {
}
