package com.ntropy.notification.client;

import org.springframework.stereotype.Component;

import com.ntropy.notification.api.client.PushSubscriptionCommandClient;
import com.ntropy.notification.api.dto.PushSubscribeCommand;
import com.ntropy.notification.config.WebPushProperties;
import com.ntropy.notification.service.PushSubscriptionService;

import lombok.RequiredArgsConstructor;

/** notification-service가 구현하는 웹푸시 구독 계약. */
@Component
@RequiredArgsConstructor
public class LocalPushSubscriptionCommandClient implements PushSubscriptionCommandClient {

    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushProperties webPushProperties;

    @Override
    public void subscribe(PushSubscribeCommand command) {
        pushSubscriptionService.subscribe(command.userId(), command.endpoint(), command.p256dh(), command.auth());
    }

    @Override
    public void unsubscribe(String endpoint) {
        pushSubscriptionService.unsubscribe(endpoint);
    }

    @Override
    public String getVapidPublicKey() {
        return webPushProperties.getPublicKey();
    }
}
