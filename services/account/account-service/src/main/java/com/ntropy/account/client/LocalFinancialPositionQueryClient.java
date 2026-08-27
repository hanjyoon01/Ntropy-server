package com.ntropy.account.client;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.FinancialPositionQueryClient;
import com.ntropy.account.api.dto.FinancialPositionSummary;
import com.ntropy.account.service.FinancialPositionService;

import lombok.RequiredArgsConstructor;

/** account-service의 금융자산·부채 집계 Service를 diagnosis-service에 연결하는 Local Client. */
@Component
@RequiredArgsConstructor
public class LocalFinancialPositionQueryClient implements FinancialPositionQueryClient {

    private final FinancialPositionService financialPositionService;

    @Override
    public FinancialPositionSummary findFinancialPosition(Long userId) {
        return financialPositionService.findFinancialPosition(userId);
    }

    @Override
    public FinancialPositionSummary findFinancialPosition(Long userId, LocalDate asOf) {
        return financialPositionService.findFinancialPosition(userId, asOf);
    }
}
