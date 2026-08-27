package com.ntropy.account.exception;

/**
 * 계좌 조회에 비밀번호가 필요하지만 저장·전송하지 않아 조회할 수 없을 때 던진다 (예: SC은행 일부 계좌).
 * 사용자·기관 전체 실패가 아니라 해당 계좌만 SKIPPED_CREDENTIAL_REQUIRED로 격리하기 위한 신호이며,
 * 이 예외 자체가 실패로 집계되면 안 된다.
 */
public class CredentialRequiredException extends RuntimeException {

    public CredentialRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
