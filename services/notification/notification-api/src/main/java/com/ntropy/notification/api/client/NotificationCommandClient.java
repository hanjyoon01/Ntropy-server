package com.ntropy.notification.api.client;

import com.ntropy.notification.api.dto.NotificationCreateCommand;
import com.ntropy.notification.api.dto.NotificationSummary;

/**
 * 알림 생성/읽음처리/삭제를 담당하는 notification-service 계약.
 * create()는 다른 도메인 서비스(defense, payment, work 등)가 이벤트 발생 시 호출한다.
 */
public interface NotificationCommandClient {

    NotificationSummary create(NotificationCreateCommand command);

    void markAsRead(Long userId, Long notificationId);

    void delete(Long userId, Long notificationId);
}
