package com.ntropy.defense.adapter.account;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.FinancialCommitmentQueryClient;
import com.ntropy.account.api.dto.FinancialCommitmentSummary;
import com.ntropy.defense.port.account.FinancialCommitment;
import com.ntropy.defense.port.account.FinancialCommitmentPort;

import lombok.RequiredArgsConstructor;

/** account-service가 발행한 FinancialCommitmentQueryClient를 defense의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class FinancialCommitmentAdapter implements FinancialCommitmentPort {

    private final FinancialCommitmentQueryClient financialCommitmentQueryClient;

    @Override
    public List<FinancialCommitment> findFinancialCommitments(Long userId, LocalDate fromDate, LocalDate toDate) {
        return financialCommitmentQueryClient.findFinancialCommitments(userId, fromDate, toDate).stream()
                .map(FinancialCommitmentAdapter::toPort)
                .toList();
    }

    private static FinancialCommitment toPort(FinancialCommitmentSummary summary) {
        return new FinancialCommitment(
                summary.getCommitmentId(),
                summary.getAccountId(),
                summary.getExpenseType(),
                summary.getProductName(),
                summary.getOutstandingBalance(),
                summary.getExpectedAmount(),
                summary.getExpectedPrincipalAmount(),
                summary.getExpectedInterestAmount(),
                summary.getNextPaymentDate(),
                summary.getAmountStatus(),
                summary.getDateStatus()
        );
    }
}
