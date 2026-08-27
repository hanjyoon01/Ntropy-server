package com.ntropy.notification.api.dto;

/**
 * 다른 도메인 서비스(defense, payment, work 등)가 이벤트 발생 시 알림 생성을 요청할 때 사용하는 커맨드.
 * eventId는 동일 이벤트로 인한 중복 알림 생성을 막기 위한 멱등성 키로 사용한다.
 */
public record NotificationCreateCommand(
        Long userId,
        String eventId,
        String notificationType,
        String title,
        String body
) {
}
