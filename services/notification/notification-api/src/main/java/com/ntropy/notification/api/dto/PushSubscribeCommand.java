package com.ntropy.notification.api.dto;

/** 브라우저의 PushSubscription.toJSON() 결과를 받아 웹푸시 구독을 등록할 때 쓰는 커맨드. */
public record PushSubscribeCommand(
        Long userId,
        String endpoint,
        String p256dh,
        String auth
) {
}
