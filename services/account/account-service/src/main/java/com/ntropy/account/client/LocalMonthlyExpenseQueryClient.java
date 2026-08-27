package com.ntropy.account.client;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.MonthlyExpenseQueryClient;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.account.service.MonthlyExpenseService;

import lombok.RequiredArgsConstructor;

/**
 * 같은 Spring 애플리케이션 내부에서
 * AI-service와 account-service를 연결하는 Local Client입니다.
 */
@Component
@RequiredArgsConstructor
public class LocalMonthlyExpenseQueryClient
        implements MonthlyExpenseQueryClient {

    private final MonthlyExpenseService monthlyExpenseService;

    /**
     * account-service의 소비 집계 Service를 호출합니다.
     */
    @Override
    public MonthlyExpenseSummary findMonthlyExpense(
            Long userId,
            String yearMonth
    ) {
        return monthlyExpenseService.findMonthlyExpense(
                userId,
                yearMonth
        );
    }
}