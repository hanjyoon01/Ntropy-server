package com.ntropy.ai.port.account;

import java.util.Map;

/**
 * ai-service가 월간 리포트에 사용하는 사용자·연월별 소비 집계. account-service의
 * MonthlyExpenseSummary와 필드 구성은 같지만, ai가 소유한 별개의 타입이다.
 */
public record MonthlyExpense(
        Long totalExpense,
        Long fixedExpense,
        Map<String, Long> categoryExpenses
) {
}
