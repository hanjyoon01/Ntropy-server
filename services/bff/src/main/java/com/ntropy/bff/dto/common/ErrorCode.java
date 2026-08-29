package com.ntropy.bff.dto.common;

import lombok.Getter;
import com.ntropy.common.exception.ServiceErrorCode;

@Getter
public enum ErrorCode implements ServiceErrorCode {

    UNAUTHORIZED(401, "인증 정보가 없습니다."),
    BAD_REQUEST(400, "잘못된 요청입니다."),
    NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int statusCode;
    private final String message;

    ErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
