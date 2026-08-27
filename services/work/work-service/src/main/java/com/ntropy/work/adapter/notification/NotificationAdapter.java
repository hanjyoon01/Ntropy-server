package com.ntropy.work.adapter.notification;

import org.springframework.stereotype.Component;

import com.ntropy.notification.api.client.NotificationCommandClient;
import com.ntropy.notification.api.dto.NotificationCreateCommand;
import com.ntropy.work.port.notification.NotificationPort;
import com.ntropy.work.port.notification.NotificationRequest;

import lombok.RequiredArgsConstructor;

/** notification-service가 발행한 NotificationCommandClient를 work의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class NotificationAdapter implements NotificationPort {

    private final NotificationCommandClient notificationCommandClient;

    @Override
    public void notify(NotificationRequest request) {
        notificationCommandClient.create(new NotificationCreateCommand(
                request.userId(),
                request.eventId(),
                request.notificationType(),
                request.title(),
                request.body()
        ));
    }
}
