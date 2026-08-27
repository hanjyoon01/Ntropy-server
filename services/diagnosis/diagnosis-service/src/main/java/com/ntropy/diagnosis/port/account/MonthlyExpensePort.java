package com.ntropy.diagnosis.port.account;

/** diagnosis-service가 정의한, account-service의 월별 소비 집계 조회 포트. */
@FunctionalInterface
public interface MonthlyExpensePort {

    MonthlyExpense findMonthlyExpense(Long userId, String yearMonth);
}
