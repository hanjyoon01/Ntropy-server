package com.ntropy.notification.api.client;

import com.ntropy.notification.api.dto.PushSubscribeCommand;

/**
 * 웹푸시 구독 등록/해제, VAPID 공개키 조회 계약. notification-service가 구현한다.
 */
public interface PushSubscriptionCommandClient {

    void subscribe(PushSubscribeCommand command);

    void unsubscribe(String endpoint);

    /** 프론트가 pushManager.subscribe()를 호출할 때 필요한 VAPID 공개키. */
    String getVapidPublicKey();
}
