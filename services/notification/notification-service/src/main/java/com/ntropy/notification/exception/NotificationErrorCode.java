package com.ntropy.notification.exception;

import com.ntropy.common.exception.ServiceErrorCode;

import lombok.Getter;

@Getter
public enum NotificationErrorCode implements ServiceErrorCode {

    NOTIFICATION_NOT_FOUND(404, "알림을 찾을 수 없습니다.");

    private final int statusCode;
    private final String message;

    NotificationErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
