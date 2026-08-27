package com.ntropy.work.port.notification;

/**
 * work-service가 알림 생성을 요청할 때 쓰는 값 타입. eventId는 동일 이벤트로 인한 중복 알림
 * 생성을 막는 멱등성 키다. notification-service의 NotificationCreateCommand와 필드 구성은
 * 같지만, work가 소유한 별개의 타입이다.
 */
public record NotificationRequest(
        Long userId,
        String eventId,
        String notificationType,
        String title,
        String body
) {
}
