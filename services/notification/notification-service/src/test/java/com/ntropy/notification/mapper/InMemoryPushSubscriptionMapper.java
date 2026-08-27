package com.ntropy.notification.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ntropy.notification.domain.entity.PushSubscription;

public class InMemoryPushSubscriptionMapper implements PushSubscriptionMapper {

    private final List<PushSubscription> store = new ArrayList<>();
    private long sequence = 1;

    @Override
    public void insert(PushSubscription subscription) {
        store.removeIf(s -> s.getEndpoint().equals(subscription.getEndpoint()));
        subscription.setSubscriptionId(sequence++);
        store.add(subscription);
    }

    @Override
    public List<PushSubscription> findByUserId(Long userId) {
        List<PushSubscription> result = new ArrayList<>();
        for (PushSubscription subscription : store) {
            if (subscription.getUserId().equals(userId)) {
                result.add(subscription);
            }
        }
        return result;
    }

    @Override
    public Optional<PushSubscription> findByEndpoint(String endpoint) {
        return store.stream().filter(s -> s.getEndpoint().equals(endpoint)).findFirst();
    }

    @Override
    public void deleteByEndpoint(String endpoint) {
        store.removeIf(s -> s.getEndpoint().equals(endpoint));
    }

    public List<PushSubscription> findAll() {
        return store;
    }
}
