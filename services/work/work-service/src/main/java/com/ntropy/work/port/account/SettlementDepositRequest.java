package com.ntropy.work.port.account;

import java.time.LocalDate;

/**
 * work-service가 계산한 플랫폼 정산금의 누적 목표액을 account-service의 가상계좌에
 * 반영해달라는 요청. account가 자신의 명령 DTO를 바꿔도 이 타입의 계약이 유지되는 한
 * work 코어는 영향받지 않는다.
 */
public record SettlementDepositRequest(
        Long userId,
        Long platformId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate depositDate,
        Long amount,
        String depositName
) {
}
