package com.ntropy.bff.dto.notification.request;

import com.ntropy.notification.api.dto.PushSubscribeCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 브라우저의 PushSubscription.toJSON() 결과를 그대로 받는 요청 DTO. */
@Getter
@NoArgsConstructor
public class PushSubscriptionRequest {

    private String endpoint;
    private Keys keys;

    public PushSubscribeCommand toCommand(Long userId) {
        return new PushSubscribeCommand(userId, endpoint, keys.p256dh, keys.auth);
    }

    @Getter
    @NoArgsConstructor
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}
