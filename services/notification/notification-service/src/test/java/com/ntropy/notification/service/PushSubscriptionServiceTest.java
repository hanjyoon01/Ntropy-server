package com.ntropy.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.notification.domain.entity.PushSubscription;
import com.ntropy.notification.mapper.InMemoryPushSubscriptionMapper;

class PushSubscriptionServiceTest {

    private final InMemoryPushSubscriptionMapper mapper = new InMemoryPushSubscriptionMapper();
    private final PushSubscriptionService service = new PushSubscriptionService(mapper);

    @Test
    void subscribe_savesSubscription() {
        service.subscribe(1L, "https://fcm.googleapis.com/endpoint-1", "p256dh-key", "auth-key");

        List<PushSubscription> saved = mapper.findByUserId(1L);
        assertEquals(1, saved.size());
        assertEquals("https://fcm.googleapis.com/endpoint-1", saved.get(0).getEndpoint());
    }

    @Test
    void subscribe_sameEndpointTwice_upsertsInsteadOfDuplicating() {
        service.subscribe(1L, "endpoint-1", "old-key", "old-auth");
        service.subscribe(1L, "endpoint-1", "new-key", "new-auth");

        List<PushSubscription> saved = mapper.findAll();
        assertEquals(1, saved.size());
        assertEquals("new-key", saved.get(0).getP256dh());
    }

    @Test
    void unsubscribe_removesSubscription() {
        service.subscribe(1L, "endpoint-1", "key", "auth");

        service.unsubscribe("endpoint-1");

        assertTrue(mapper.findByUserId(1L).isEmpty());
    }
}
