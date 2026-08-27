package com.ntropy.user.exception;

import com.ntropy.common.exception.ServiceErrorCode;

import lombok.Getter;

@Getter
public enum UserErrorCode implements ServiceErrorCode {

    UNSUPPORTED_OAUTH_PROVIDER(400, "지원하지 않는 소셜 로그인 제공자입니다."),
    INVALID_REQUEST(400, "요청 값이 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(401, "유효하지 않은 Refresh Token입니다."),
    INACTIVE_USER(403, "로그인할 수 없는 회원 상태입니다."),
    USER_NOT_FOUND(404, "회원을 찾을 수 없습니다."),
    VIRTUAL_TEST_DISABLED(404, "가상회원 테스트 로그인이 비활성화되어 있습니다.");

    private final int statusCode;
    private final String message;

    UserErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
