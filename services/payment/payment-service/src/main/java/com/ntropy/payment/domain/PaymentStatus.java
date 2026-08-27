package com.ntropy.payment.domain;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    RETRY,
    CANCELLED
}