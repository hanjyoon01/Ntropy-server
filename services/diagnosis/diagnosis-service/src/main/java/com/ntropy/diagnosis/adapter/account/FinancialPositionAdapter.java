package com.ntropy.diagnosis.adapter.account;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.FinancialPositionQueryClient;
import com.ntropy.account.api.dto.FinancialPositionSummary;
import com.ntropy.diagnosis.port.account.FinancialPosition;
import com.ntropy.diagnosis.port.account.FinancialPositionPort;

import lombok.RequiredArgsConstructor;

/** account-service가 발행한 FinancialPositionQueryClient를 diagnosis의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class FinancialPositionAdapter implements FinancialPositionPort {

    private final FinancialPositionQueryClient financialPositionQueryClient;

    @Override
    public FinancialPosition findFinancialPosition(Long userId) {
        return toPort(financialPositionQueryClient.findFinancialPosition(userId));
    }

    @Override
    public FinancialPosition findFinancialPosition(Long userId, LocalDate asOf) {
        return toPort(financialPositionQueryClient.findFinancialPosition(userId, asOf));
    }

    private static FinancialPosition toPort(FinancialPositionSummary summary) {
        if (summary == null) {
            return null;
        }
        return new FinancialPosition(
                summary.totalFinancialAssets(),
                summary.liquidAssets(),
                summary.safeAssets()
        );
    }
}
