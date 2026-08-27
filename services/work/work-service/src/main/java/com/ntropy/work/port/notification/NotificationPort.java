package com.ntropy.work.port.notification;

/** work-service가 정의한, notification-service의 알림 생성 포트. */
public interface NotificationPort {

    void notify(NotificationRequest request);
}
