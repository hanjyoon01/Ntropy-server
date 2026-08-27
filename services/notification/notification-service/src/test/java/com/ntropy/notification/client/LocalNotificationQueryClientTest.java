package com.ntropy.notification.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ntropy.common.dto.account.PageSummary;
import com.ntropy.notification.api.dto.NotificationSummary;
import com.ntropy.notification.service.NotificationServiceTestSupport;

class LocalNotificationQueryClientTest {

    @Test
    void findNotificationsReturnsOnlyRequestedUsersPage() {
        NotificationServiceTestSupport support = NotificationServiceTestSupport.withAlarmAgree(1L, true);
        support.service().createNotification(1L, "evt-1", "DEFENSE_MODE", "제목1", "본문1");
        support.service().createNotification(1L, "evt-2", "DEFENSE_MODE", "제목2", "본문2");
        LocalNotificationQueryClient client = new LocalNotificationQueryClient(support.service());

        PageSummary<NotificationSummary> page = client.findNotifications(1L, 0, 10);

        assertEquals(2, page.content().size());
        assertEquals(2, page.totalElements());
    }

    @Test
    void countUnreadDelegatesToService() {
        NotificationServiceTestSupport support = NotificationServiceTestSupport.withAlarmAgree(1L, true);
        support.service().createNotification(1L, "evt-1", "DEFENSE_MODE", "제목", "본문");
        LocalNotificationQueryClient client = new LocalNotificationQueryClient(support.service());

        assertEquals(1, client.countUnread(1L));
    }
}
