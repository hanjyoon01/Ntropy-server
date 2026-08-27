package com.ntropy.account.api.client;

import com.ntropy.account.api.dto.MonthlyExpenseSummary;

/**
 * account-service의 월별 소비 집계 조회 계약입니다.
 *
 * AI-service가 account-service의 DB를 직접 조회하지 않고,
 * 공통 Client를 통해 소비 집계 결과를 전달받도록 합니다.
 */
public interface MonthlyExpenseQueryClient {

    /**
     * 특정 사용자의 특정 연월 소비를 조회합니다.
     *
     * @param userId 사용자 ID
     * @param yearMonth 조회 연월. 예: 2026-07
     * @return 월별 총소비 및 카테고리별 소비 금액
     */
    MonthlyExpenseSummary findMonthlyExpense(
            Long userId,
            String yearMonth
    );
}
