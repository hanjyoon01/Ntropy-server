package com.ntropy.diagnosis.adapter.account;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.MonthlyExpenseQueryClient;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.diagnosis.port.account.MonthlyExpense;
import com.ntropy.diagnosis.port.account.MonthlyExpensePort;

import lombok.RequiredArgsConstructor;

/** account-service가 발행한 MonthlyExpenseQueryClient를 diagnosis의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class MonthlyExpenseAdapter implements MonthlyExpensePort {

    private final MonthlyExpenseQueryClient monthlyExpenseQueryClient;

    @Override
    public MonthlyExpense findMonthlyExpense(Long userId, String yearMonth) {
        MonthlyExpenseSummary summary = monthlyExpenseQueryClient.findMonthlyExpense(userId, yearMonth);
        if (summary == null) {
            return null;
        }
        return new MonthlyExpense(summary.getTotalExpense(), summary.getFixedExpense());
    }
}
