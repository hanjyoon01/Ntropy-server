package com.ntropy.defense.port.account;

import java.time.LocalDate;

/**
 * defense-service가 방어모드 고정지출 점검에 사용하는 금융 납입 예정 항목.
 * account-service의 FinancialCommitmentSummary와 필드 구성은 같지만, defense가 소유한
 * 별개의 타입이다.
 */
public record FinancialCommitment(
        Long commitmentId,
        Long accountId,
        String expenseType,
        String productName,
        Long outstandingBalance,
        Long expectedAmount,
        Long expectedPrincipalAmount,
        Long expectedInterestAmount,
        LocalDate nextPaymentDate,
        String amountStatus,
        String dateStatus
) {
}
