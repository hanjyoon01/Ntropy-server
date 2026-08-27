package com.ntropy.notification.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.notification.api.client.NotificationQueryClient;
import com.ntropy.common.dto.account.PageSummary;
import com.ntropy.notification.api.dto.NotificationSummary;
import com.ntropy.notification.domain.entity.Notification;
import com.ntropy.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** notification-service가 구현하는 알림 조회 계약. */
@Component
@RequiredArgsConstructor
public class LocalNotificationQueryClient implements NotificationQueryClient {

    private final NotificationService notificationService;

    @Override
    public PageSummary<NotificationSummary> findNotifications(Long userId, int page, int size) {
        List<Notification> notifications = notificationService.getNotifications(userId, page, size);
        long totalElements = notificationService.countNotifications(userId);

        List<NotificationSummary> content = notifications.stream()
                .map(this::toSummary)
                .toList();

        return PageSummary.of(content, page, size, totalElements);
    }

    @Override
    public long countUnread(Long userId) {
        return notificationService.countUnread(userId);
    }

    private NotificationSummary toSummary(Notification notification) {
        return new NotificationSummary(
                notification.getNotificationId(),
                notification.getEventId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
