package com.ntropy.notification.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.notification.domain.entity.PushSubscription;
import com.ntropy.notification.mapper.InMemoryPushSubscriptionMapper;

class WebPushSenderTest {

    private final InMemoryPushSubscriptionMapper mapper = new InMemoryPushSubscriptionMapper();
    private final StubWebPushClient client = new StubWebPushClient();
    private final WebPushSender sender = new WebPushSender(mapper, client);

    @Test
    void sendToUser_noSubscriptions_doesNothing() {
        sender.sendToUser(1L, Map.of("title", "제목"));

        assertTrue(client.sentToEndpoints.isEmpty());
    }

    @Test
    void sendToUser_hasSubscription_sendsToEndpoint() {
        mapper.insert(subscription(1L, "endpoint-1"));

        sender.sendToUser(1L, Map.of("title", "제목"));

        assertEquals(1, client.sentToEndpoints.size());
        assertEquals("endpoint-1", client.sentToEndpoints.get(0));
    }

    @Test
    void sendToUser_statusGone_removesSubscription() {
        mapper.insert(subscription(1L, "endpoint-1"));
        client.willReturnStatus(410);

        sender.sendToUser(1L, Map.of("title", "제목"));

        assertTrue(mapper.findByUserId(1L).isEmpty());
    }

    @Test
    void sendToUser_statusNotFound_removesSubscription() {
        mapper.insert(subscription(1L, "endpoint-1"));
        client.willReturnStatus(404);

        sender.sendToUser(1L, Map.of("title", "제목"));

        assertTrue(mapper.findByUserId(1L).isEmpty());
    }

    @Test
    void sendToUser_clientThrows_doesNotPropagateAndKeepsSubscription() {
        mapper.insert(subscription(1L, "endpoint-1"));
        client.willThrow();

        sender.sendToUser(1L, Map.of("title", "제목"));

        assertEquals(1, mapper.findByUserId(1L).size());
    }

    private static PushSubscription subscription(Long userId, String endpoint) {
        return PushSubscription.builder()
                .userId(userId)
                .endpoint(endpoint)
                .p256dh("p256dh-key")
                .auth("auth-key")
                .build();
    }
}
