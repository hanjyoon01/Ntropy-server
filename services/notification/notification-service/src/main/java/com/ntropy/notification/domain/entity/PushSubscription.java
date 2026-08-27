package com.ntropy.notification.domain.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    private Long subscriptionId;   // subscription_id
    private Long userId;           // user_id
    private String endpoint;       // endpoint - 브라우저 푸시 서비스 URL, 구독 단위 식별자
    private String p256dh;         // p256dh - 암호화 공개키
    private String auth;           // auth - 인증 시크릿
    private LocalDateTime createdAt; // created_at
}
