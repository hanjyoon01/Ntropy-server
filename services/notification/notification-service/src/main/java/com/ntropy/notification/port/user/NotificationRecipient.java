package com.ntropy.notification.port.user;

/**
 * notification-service가 알림 발송 가능 여부를 판단할 때 필요로 하는 회원 정보.
 * user-service의 UserSummary와 필드 구성은 같지만, notification이 소유한 별개의 타입이다.
 */
public record NotificationRecipient(
        Long userId,
        Boolean alarmAgree
) {
}
