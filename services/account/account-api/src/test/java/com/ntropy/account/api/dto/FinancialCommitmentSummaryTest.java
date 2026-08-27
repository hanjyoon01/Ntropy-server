package com.ntropy.account.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * 대출 원금·이자 분리 이전에 9개 인자로 호출하던 defense-service 등 기존 코드가
 * 그대로 컴파일·동작하는지 확인한다.
 */
class FinancialCommitmentSummaryTest {

    @Test
    void legacyNineArgConstructorLeavesPrincipalAndInterestNull() {
        FinancialCommitmentSummary summary = new FinancialCommitmentSummary(
                1L, 10L, "SAVING_PAYMENT", "청년희망적금", null,
                500_000L, LocalDate.of(2026, 8, 5), "CONFIRMED", "ESTIMATED");

        assertEquals(500_000L, summary.getExpectedAmount());
        assertNull(summary.getExpectedPrincipalAmount());
        assertNull(summary.getExpectedInterestAmount());
    }

    @Test
    void fullConstructorSetsPrincipalAndInterest() {
        FinancialCommitmentSummary summary = new FinancialCommitmentSummary(
                2L, 20L, "LOAN_REPAYMENT", "신한 직장인 대출", 12_500_000L,
                250_000L, 200_000L, 50_000L,
                LocalDate.of(2026, 8, 25), "ESTIMATED", "ESTIMATED");

        assertEquals(250_000L, summary.getExpectedAmount());
        assertEquals(200_000L, summary.getExpectedPrincipalAmount());
        assertEquals(50_000L, summary.getExpectedInterestAmount());
    }
}
