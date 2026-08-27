package com.ntropy.notification.service;

import com.ntropy.notification.mapper.InMemoryPushSubscriptionMapper;
import com.ntropy.notification.push.StubWebPushClient;
import com.ntropy.notification.push.WebPushSender;

/** 다른 패키지의 테스트(client 계층 등)에서 NotificationService를 조립할 때 쓰는 헬퍼. */
public class NotificationServiceTestSupport {

    private final NotificationService service;

    private NotificationServiceTestSupport(NotificationService service) {
        this.service = service;
    }

    public static NotificationServiceTestSupport withAlarmAgree(Long userId, boolean alarmAgree) {
        InMemoryNotificationMapper mapper = new InMemoryNotificationMapper();
        StubUserQueryClient userQueryClient = new StubUserQueryClient().withAlarmAgree(userId, alarmAgree);
        WebPushSender webPushSender = new WebPushSender(new InMemoryPushSubscriptionMapper(), new StubWebPushClient());
        return new NotificationServiceTestSupport(
                new NotificationService(mapper, userQueryClient, webPushSender));
    }

    public NotificationService service() {
        return service;
    }
}



