package com.ntropy.ai.adapter.account;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.MonthlyExpenseQueryClient;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.ai.port.account.MonthlyExpense;
import com.ntropy.ai.port.account.MonthlyExpensePort;

import lombok.RequiredArgsConstructor;

/** account-service가 발행한 MonthlyExpenseQueryClient를 ai의 포트로 번역한다. */
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
        return new MonthlyExpense(summary.getTotalExpense(), summary.getFixedExpense(), summary.getCategoryExpenses());
    }
}
