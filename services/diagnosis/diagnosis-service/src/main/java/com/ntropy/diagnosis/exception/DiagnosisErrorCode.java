package com.ntropy.diagnosis.exception;

import com.ntropy.common.exception.ServiceErrorCode;

import lombok.Getter;

/**
 * diagnosis-service에서 발생하는 재무진단 조회 관련 오류를 정의합니다.
 */
@Getter
public enum DiagnosisErrorCode implements ServiceErrorCode {

    INVALID_REQUEST(400, "재무진단 조회 요청값이 올바르지 않습니다."),
    DIAGNOSIS_RESULT_NOT_FOUND(404, "재무진단 결과를 찾을 수 없습니다."),
    INVALID_CALCULATION_INPUT(500, "재무진단 계산에 사용된 집계 데이터가 올바르지 않습니다.");

    private final int statusCode;
    private final String message;

    DiagnosisErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
