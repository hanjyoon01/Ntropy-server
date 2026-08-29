package com.ntropy.bff.dto.subscription.response;

/** 구독 API 응답 상태. */
public enum SubscriptionStatus {
    ACTIVE,
    CANCEL_SCHEDULED,
    EXPIRED,
    PAYMENT_FAILED
}
