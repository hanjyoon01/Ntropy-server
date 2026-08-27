package com.ntropy.notification.api.client;

import com.ntropy.common.dto.account.PageSummary;
import com.ntropy.notification.api.dto.NotificationSummary;

/** 로그인 사용자의 알림 이력을 조회하는 notification-service 계약. */
public interface NotificationQueryClient {

    PageSummary<NotificationSummary> findNotifications(Long userId, int page, int size);

    long countUnread(Long userId);
}
