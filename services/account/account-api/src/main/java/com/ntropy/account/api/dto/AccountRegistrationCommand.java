package com.ntropy.account.api.dto;

/** 금융계좌 등록 명령. 은행 로그인 정보는 CODEF 요청에만 사용하고 저장하지 않는다. */
public record AccountRegistrationCommand(
        String connectionType,
        String organizationCode,
        String bankLoginId,
        String bankLoginPassword,
        String birthDate
) {
}
