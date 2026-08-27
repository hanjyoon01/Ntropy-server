package com.ntropy.user.api.dto;

/** 외부 조회 계약에 노출하는 회원 정보. 인증 토큰과 소셜 제공자 식별자는 포함하지 않는다. */
public record UserSummary(
        Long userId,
        String name,
        String email,
        String provider,
        Boolean alarmAgree,
        Boolean locationAgree,
        Boolean onboardingCompleted
) {
}
