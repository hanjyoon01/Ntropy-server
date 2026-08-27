package com.ntropy.ai.exception;

import com.ntropy.common.exception.ServiceErrorCode;

import lombok.Getter;

/**
 * ai-service에서 발생하는 AI 리포트 관련 오류를 정의합니다.
 *
 * ServiceException에 이 오류 코드를 전달하면,
 * 공통 예외 처리기가 상태 코드와 메시지를 응답에 활용합니다.
 */
@Getter
public enum AiReportErrorCode implements ServiceErrorCode {

    // userId 또는 yearMonth 요청값이 올바르지 않은 경우
    INVALID_REQUEST(400, "AI 리포트 조회 요청값이 올바르지 않습니다."),

    // 해당 사용자·연월 조합의 리포트가 없는 경우
    REPORT_NOT_FOUND(404, "AI 리포트를 찾을 수 없습니다."),

    REPORT_JSON_INVALID(500, "AI 리포트 JSON 데이터 형식이 올바르지 않습니다."),

    EMAIL_DELIVERY_FORBIDDEN(403, "AI 리포트 이메일 내보내기는 활성 구독자만 사용할 수 있습니다."),
    EMAIL_NOT_AVAILABLE(422, "발송 가능한 가입 이메일이 없습니다."),
    PDF_GENERATION_FAILED(500, "AI 리포트 PDF를 생성하지 못했습니다."),
    EMAIL_CONFIGURATION_INVALID(500, "이메일 발송 설정이 올바르지 않습니다."),
    EMAIL_DELIVERY_FAILED(502, "AI 리포트 이메일 발송에 실패했습니다.");

    private final int statusCode;
    private final String message;

    AiReportErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
