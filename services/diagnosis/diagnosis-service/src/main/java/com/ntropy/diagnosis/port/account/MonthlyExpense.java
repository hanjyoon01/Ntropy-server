package com.ntropy.diagnosis.port.account;

/**
 * diagnosis-service가 재무진단에 사용하는 사용자·연월별 소비 집계. account-service의
 * MonthlyExpenseSummary와 필드 구성은 비슷하지만, diagnosis가 소유한 별개의 타입이다.
 */
public record MonthlyExpense(
        Long totalExpense,
        Long fixedExpense
) {
}
