package com.ntropy.defense.exception;

import com.ntropy.common.exception.ServiceErrorCode;
import lombok.Getter;

@Getter
public enum DefenseErrorCode implements ServiceErrorCode {
    INVALID_REQUEST(400, "방어모드 요청값이 올바르지 않습니다."),
    INVALID_CAUSE(400, "지원하지 않는 방어모드 원인입니다."),
    INVALID_PERIOD(400, "예상 복귀일은 근무불가 시작일보다 빠를 수 없습니다."),
    ALREADY_ACTIVE(409, "이미 활성화되었거나 예약된 방어모드가 있습니다."),
    NOT_FOUND(404, "방어모드를 찾을 수 없습니다."),
    NOT_ACTIVE(409, "활성 상태의 방어모드가 아닙니다."),
    ACCESS_DENIED(403, "해당 방어모드에 접근할 수 없습니다.");

    private final int statusCode;
    private final String message;

    DefenseErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
