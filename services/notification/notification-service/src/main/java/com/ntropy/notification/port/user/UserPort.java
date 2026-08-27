package com.ntropy.notification.port.user;

/** notification-service가 정의한, user-service의 회원 조회 포트. */
public interface UserPort {

    NotificationRecipient findRecipient(Long userId);
}
