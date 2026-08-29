package com.ntropy.bff.dto.subscription.response;

/** 결제 내역 API 응답 상태. */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    RETRY,
    CANCELLED
}
