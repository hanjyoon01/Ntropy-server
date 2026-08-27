package com.ntropy.notification.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.ntropy.notification.domain.entity.Notification;
import com.ntropy.notification.mapper.NotificationMapper;

/** 실제 DB 없이 NotificationService 로직을 검증하기 위한 인메모리 매퍼 구현. */
class InMemoryNotificationMapper implements NotificationMapper {

    final List<Notification> rows = new ArrayList<>();
    private long sequence = 1L;

    @Override
    public void insertNotification(Notification notification) {
        notification.setNotificationId(sequence++);
        rows.add(notification);
    }

    @Override
    public Optional<Notification> findByEventId(String eventId) {
        return rows.stream().filter(n -> n.getEventId().equals(eventId)).findFirst();
    }

    @Override
    public Optional<Notification> findByIdAndUserId(Long notificationId, Long userId) {
        return rows.stream()
                .filter(n -> n.getNotificationId().equals(notificationId)
                        && n.getUserId().equals(userId)
                        && n.getDeletedAt() == null)
                .findFirst();
    }

    @Override
    public List<Notification> findByUserId(Long userId, int offset, int limit) {
        return rows.stream()
                .filter(n -> n.getUserId().equals(userId) && n.getDeletedAt() == null)
                .sorted(Comparator.comparing(Notification::getNotificationId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countByUserId(Long userId) {
        return rows.stream().filter(n -> n.getUserId().equals(userId) && n.getDeletedAt() == null).count();
    }

    @Override
    public long countUnreadByUserId(Long userId) {
        return rows.stream()
                .filter(n -> n.getUserId().equals(userId) && n.getDeletedAt() == null && n.getReadAt() == null)
                .count();
    }

    @Override
    public void markAsRead(Long notificationId) {
        rows.stream().filter(n -> n.getNotificationId().equals(notificationId)).findFirst()
                .ifPresent(n -> n.setReadAt(java.time.LocalDateTime.now()));
    }

    @Override
    public void softDelete(Long notificationId) {
        rows.stream().filter(n -> n.getNotificationId().equals(notificationId)).findFirst()
                .ifPresent(n -> n.setDeletedAt(java.time.LocalDateTime.now()));
    }
}
