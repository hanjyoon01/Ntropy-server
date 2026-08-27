package com.ntropy.work.port.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * work-service가 정산 매칭에 사용하는 입금 거래. account-service의 내부 표현
 * (NormalizedIncomingTransaction)과 필드 구성은 같지만, work가 소유한 별개의 타입이다 -
 * account가 자신의 DTO를 바꿔도 이 타입의 계약이 유지되는 한 work 코어는 영향받지 않는다.
 */
public record IncomingTransaction(
        Long transactionId,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String counterpartyName,
        BigDecimal amount
) {
}
