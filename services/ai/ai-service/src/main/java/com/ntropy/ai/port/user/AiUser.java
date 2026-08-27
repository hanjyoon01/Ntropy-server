package com.ntropy.ai.port.user;

/**
 * ai-service가 리포트 이메일 발송 대상 확인에 필요로 하는 회원 정보.
 * user-service의 UserSummary와 필드 구성은 다르지만(필요한 필드만 추림), ai가 소유한
 * 별개의 타입이다.
 */
public record AiUser(
        Long userId,
        String email
) {
}
