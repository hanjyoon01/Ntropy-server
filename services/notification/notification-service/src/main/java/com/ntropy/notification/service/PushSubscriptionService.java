package com.ntropy.notification.service;

import org.springframework.stereotype.Service;

import com.ntropy.notification.domain.entity.PushSubscription;
import com.ntropy.notification.mapper.PushSubscriptionMapper;

import lombok.RequiredArgsConstructor;

/** 웹푸시 구독 등록/해제를 담당한다. 실제 발송은 WebPushSender가 맡는다. */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionMapper pushSubscriptionMapper;

    /** 같은 endpoint로 재구독하면 upsert된다 (PushSubscriptionMapper.xml의 ON DUPLICATE KEY UPDATE). */
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        PushSubscription subscription = PushSubscription.builder()
                .userId(userId)
                .endpoint(endpoint)
                .p256dh(p256dh)
                .auth(auth)
                .build();
        pushSubscriptionMapper.insert(subscription);
    }

    public void unsubscribe(String endpoint) {
        pushSubscriptionMapper.deleteByEndpoint(endpoint);
    }
}
