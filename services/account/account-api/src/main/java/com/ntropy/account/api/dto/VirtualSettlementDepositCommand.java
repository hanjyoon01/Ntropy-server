package com.ntropy.account.api.dto;

import java.time.LocalDate;

/**
 * work-service가 계산한 플랫폼 정산금의 누적 목표액을 NTROPY 가상 수시입출금 계좌에 기록하는 명령.
 * 사용자·플랫폼·정산기간은 account-service가 멱등 fingerprint를 만드는 논리 키로 사용한다.
 */
public record VirtualSettlementDepositCommand(
        Long userId,
        Long platformId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate depositDate,
        Long amount,
        String depositName
) {
}
