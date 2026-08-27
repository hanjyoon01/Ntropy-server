package com.ntropy.notification.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.ntropy.notification.api.dto.NotificationCreateCommand;
import com.ntropy.notification.api.dto.NotificationSummary;
import com.ntropy.notification.service.NotificationServiceTestSupport;

class LocalNotificationCommandClientTest {

    @Test
    void createReturnsSummaryWhenAlarmAgreeIsTrue() {
        NotificationServiceTestSupport support = NotificationServiceTestSupport.withAlarmAgree(1L, true);
        LocalNotificationCommandClient client = new LocalNotificationCommandClient(support.service());

        NotificationSummary summary = client.create(
                new NotificationCreateCommand(1L, "evt-1", "DEFENSE_MODE", "제목", "본문"));

        assertEquals("제목", summary.title());
        assertEquals("evt-1", summary.eventId());
    }

    @Test
    void createReturnsNullWhenAlarmAgreeIsFalse() {
        NotificationServiceTestSupport support = NotificationServiceTestSupport.withAlarmAgree(1L, false);
        LocalNotificationCommandClient client = new LocalNotificationCommandClient(support.service());

        NotificationSummary summary = client.create(
                new NotificationCreateCommand(1L, "evt-1", "DEFENSE_MODE", "제목", "본문"));

        assertNull(summary);
    }
}
