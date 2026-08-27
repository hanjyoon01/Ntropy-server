package com.ntropy.ai.adapter.payment;

import org.springframework.stereotype.Component;

import com.ntropy.ai.port.payment.SubscriptionPort;
import com.ntropy.common.domain.Feature;
import com.ntropy.payment.api.client.SubscriptionQueryClient;

import lombok.RequiredArgsConstructor;

/** payment-service가 발행한 SubscriptionQueryClient를 ai의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class SubscriptionAdapter implements SubscriptionPort {

    private final SubscriptionQueryClient subscriptionQueryClient;

    @Override
    public boolean supportsFeature(Long userId, Feature feature) {
        return subscriptionQueryClient.supportsFeature(userId, feature);
    }
}
