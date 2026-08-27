package com.ntropy.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.notification.domain.entity.Notification;
import com.ntropy.notification.mapper.InMemoryPushSubscriptionMapper;
import com.ntropy.notification.push.StubWebPushClient;
import com.ntropy.notification.push.WebPushSender;

class NotificationServiceTest {

    private final InMemoryNotificationMapper mapper = new InMemoryNotificationMapper();
    private final StubUserQueryClient userQueryClient = new StubUserQueryClient();
    private final NotificationService service = new NotificationService(mapper, userQueryClient,
            new WebPushSender(new InMemoryPushSubscriptionMapper(), new StubWebPushClient()));

    @Test
    void createsNotificationWhenAlarmAgreeIsTrue() {
        userQueryClient.withAlarmAgree(1L, true);

        Optional<Notification> result = service.createNotification(1L, "evt-1", "DEFENSE_MODE", "제목", "본문");

        assertTrue(result.isPresent());
        assertNotNull(result.get().getNotificationId());
        assertEquals(1, mapper.rows.size());
    }

    @Test
    void skipsCreationWhenAlarmAgreeIsFalse() {
        userQueryClient.withAlarmAgree(1L, false);

        Optional<Notification> result = service.createNotification(1L, "evt-1", "DEFENSE_MODE", "제목", "본문");

        assertTrue(result.isEmpty());
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void skipsCreationWhenUserDoesNotExist() {
        Optional<Notification> result = service.createNotification(999L, "evt-1", "DEFENSE_MODE", "제목", "본문");

        assertTrue(result.isEmpty());
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void returnsExistingNotificationInsteadOfDuplicatingOnSameEventId() {
        userQueryClient.withAlarmAgree(1L, true);
        service.createNotification(1L, "evt-1", "DEFENSE_MODE", "제목", "본문");

        Optional<Notification> second = service.createNotification(1L, "evt-1", "DEFENSE_MODE", "다른 제목", "다른 본문");

        assertEquals(1, mapper.rows.size());
        assertEquals("제목", second.orElseThrow().getTitle());
    }

    @Test
    void marksOwnedNotificationAsRead() {
        userQueryClient.withAlarmAgree(1L, true);
        Notification notification = service.createNotification(1L, "evt-1", "DEFENSE_MODE", "제목", "본문").orElseThrow();

        service.markAsRead(1L, notification.getNotificationId());

        assertNotNull(mapper.rows.get(0).getReadAt());
        assertEquals(0, service.countUnread(1L));
    }

    @Test
    void rejectsMarkAsReadForNonOwner() {
        userQueryClient.withAlarmAgree(1L, true);
        Notification notification = service.createNotification(1L, "evt-1", "DEFENSE_MODE", "제목", "본문").orElseThrow();

        assertThrows(ServiceException.class, () -> service.markAsRead(2L, notification.getNotificationId()));
    }

    @Test
    void softDeletesOwnedNotificationAndExcludesFromCounts() {
        userQueryClient.withAlarmAgree(1L, true);
        Notification notification = service.createNotification(1L, "evt-1", "DEFENSE_MODE", "제목", "본문").orElseThrow();

        service.delete(1L, notification.getNotificationId());

        assertNotNull(mapper.rows.get(0).getDeletedAt());
        assertEquals(0, service.countNotifications(1L));
        assertFalse(service.getNotifications(1L, 0, 10).contains(notification));
    }
}
