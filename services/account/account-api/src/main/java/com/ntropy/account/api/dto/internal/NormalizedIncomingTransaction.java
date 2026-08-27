package com.ntropy.account.api.dto.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 은행별 원본 거래를 서비스 간 전달용 공통 입금 거래 구조로 가공한 결과.
 * counterpartyName은 은행별 필드 선택과 검증된 은행별 접두사 제거를 마친 값이다.
 * 플랫폼 비교를 위한 Unicode, 대소문자, 공백 및 기호 정규화는 적용하지 않는다.
 */
public record NormalizedIncomingTransaction(
        Long transactionId,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String counterpartyName,
        BigDecimal amount
) {
}
