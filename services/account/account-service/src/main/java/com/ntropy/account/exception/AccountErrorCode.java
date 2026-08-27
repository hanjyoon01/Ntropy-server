package com.ntropy.account.exception;

import com.ntropy.common.exception.ServiceErrorCode;

import lombok.Getter;

@Getter
public enum AccountErrorCode implements ServiceErrorCode {

    UNSUPPORTED_BANK(400, "지원하지 않는 은행 기관코드입니다."),
    INVALID_REQUEST(400, "요청 값이 올바르지 않습니다."),
    BIRTH_DATE_REQUIRED(400, "생년월일이 필요합니다."),
    BIRTH_DATE_INVALID(400, "생년월일 형식이 올바르지 않습니다."),
    BIRTH_DATE_MISMATCH(400, "생년월일이 일치하지 않습니다."),
    ACCOUNT_NOT_FOUND(404, "계좌를 찾을 수 없습니다."),
    TRANSACTION_ANALYSIS_TARGET_INVALID(400, "요청한 거래 중 해당 사용자·연월의 분류 대상이 아닌 거래가 포함되어 있습니다."),
    FINANCIAL_POSITION_BALANCE_INVALID(500, "집계 대상 계좌의 잔액이 올바르지 않아 금융자산·부채를 계산할 수 없습니다."),
    FINANCIAL_POSITION_OVERFLOW(500, "금융자산·부채 합산 결과가 허용 범위를 초과했습니다.");

    private final int statusCode;
    private final String message;

    AccountErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
