package com.ntropy.notification.client;

import org.springframework.stereotype.Component;

import com.ntropy.notification.api.client.NotificationCommandClient;
import com.ntropy.notification.api.dto.NotificationCreateCommand;
import com.ntropy.notification.api.dto.NotificationSummary;
import com.ntropy.notification.domain.entity.Notification;
import com.ntropy.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** notification-service가 구현하는 알림 생성/읽음처리/삭제 계약. */
@Component
@RequiredArgsConstructor
public class LocalNotificationCommandClient implements NotificationCommandClient {

    private final NotificationService notificationService;

    /** 알림 수신 동의가 꺼져 있으면 실제로 생성되지 않으므로 null을 반환할 수 있다. */
    @Override
    public NotificationSummary create(NotificationCreateCommand command) {
        return notificationService.createNotification(
                        command.userId(),
                        command.eventId(),
                        command.notificationType(),
                        command.title(),
                        command.body()
                )
                .map(this::toSummary)
                .orElse(null);
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

    @Override
    public void markAsRead(Long userId, Long notificationId) {
        notificationService.markAsRead(userId, notificationId);
    }

    @Override
    public void delete(Long userId, Long notificationId) {
        notificationService.delete(userId, notificationId);
    }
}
