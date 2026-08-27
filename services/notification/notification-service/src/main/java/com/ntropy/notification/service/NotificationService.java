package com.ntropy.notification.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.notification.domain.entity.Notification;
import com.ntropy.notification.exception.NotificationErrorCode;
import com.ntropy.notification.mapper.NotificationMapper;
import com.ntropy.notification.port.user.NotificationRecipient;
import com.ntropy.notification.port.user.UserPort;
import com.ntropy.notification.push.WebPushSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserPort userPort;
    private final WebPushSender webPushSender;

    /** 알림 수신 동의(alarm_agree)가 꺼져 있으면 발송(=생성) 자체를 건너뛴다. null은 존재하지 않는 회원이므로 발송하지 않는다. */
    @Transactional
    public Optional<Notification> createNotification(Long userId, String eventId, String notificationType, String title, String body) {
        Optional<Notification> existing = notificationMapper.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("이미 처리된 이벤트라 알림 생성을 건너뜁니다: eventId={}", eventId);
            return existing;
        }

        NotificationRecipient user = userPort.findRecipient(userId);
        if (user == null || !Boolean.TRUE.equals(user.alarmAgree())) {
            log.info("알림 수신 동의가 꺼져 있어 알림 생성을 건너뜁니다: userId={}, eventId={}", userId, eventId);
            return Optional.empty();
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .eventId(eventId)
                .notificationType(notificationType)
                .title(title)
                .body(body)
                .build();

        notificationMapper.insertNotification(notification);
        webPushSender.sendToUser(userId, Map.of(
                "notificationId", notification.getNotificationId(),
                "notificationType", notification.getNotificationType(),
                "title", notification.getTitle(),
                "body", notification.getBody()
        ));
        return Optional.of(notification);
    }

    public List<Notification> getNotifications(Long userId, int page, int size) {
        return notificationMapper.findByUserId(userId, page * size, size);
    }

    public long countNotifications(Long userId) {
        return notificationMapper.countByUserId(userId);
    }

    public long countUnread(Long userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = getOwnedNotification(userId, notificationId);
        notificationMapper.markAsRead(notification.getNotificationId());
    }

    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification notification = getOwnedNotification(userId, notificationId);
        notificationMapper.softDelete(notification.getNotificationId());
    }

    private Notification getOwnedNotification(Long userId, Long notificationId) {
        return notificationMapper.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ServiceException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }
}
