package com.ntropy.account.api.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoanDisbursementKeywordsTest {

    @Test
    void keywordListIsNeverEmpty() {
        assertFalse(
                LoanDisbursementKeywords.KEYWORDS.isEmpty(),
                "MyBatis <foreach>가 빈 목록으로는 유효한 조건을 생성하지 못하므로 " +
                        "목록이 비어 있으면 안 됩니다"
        );
    }

    @Test
    void nullIsNotDisbursement() {
        assertFalse(LoanDisbursementKeywords.matches(null));
    }

    @Test
    void blankIsNotDisbursement() {
        assertFalse(LoanDisbursementKeywords.matches(""));
        assertFalse(LoanDisbursementKeywords.matches("   "));
    }

    @Test
    void normalRepaymentIsNotDisbursement() {
        assertFalse(LoanDisbursementKeywords.matches("정상상환"));
    }

    @Test
    void newLoanIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("신규"));
    }

    @Test
    void executionIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("실행"));
    }

    @Test
    void increaseIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("증액"));
    }

    @Test
    void loanExecutionIsDisbursementBecauseItContainsExecution() {
        assertTrue(LoanDisbursementKeywords.matches("대출실행"));
    }

    @Test
    void loanExecutionWithInternalSpaceIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("대출 실행"));
    }

    @Test
    void newLoanWithInternalSpaceIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("신 규"));
    }

    @Test
    void executionWithInternalSpaceIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("실 행"));
    }

    @Test
    void increaseWithInternalSpaceIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("증 액"));
    }

    @Test
    void keywordInMiddleOfStringIsDisbursement() {
        assertTrue(LoanDisbursementKeywords.matches("2026년 신규 대출 실행분"));
    }
}
