package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ntropy.ai.port.account.ClassificationTargetTransaction;
import com.ntropy.ai.port.account.TransactionAnalysisResult;

class TransactionPreClassificationServiceTest {

    private final TransactionPreClassificationService service =
            new TransactionPreClassificationService();

    @Test
    void normalLoanRepaymentIsFixedFinanceConsumption() {
        TransactionAnalysisResult result = service.classify(
                target(
                        "LOAN",
                        null,
                        null
                )
        ).orElseThrow();

        assertTrue(result.isConsumption());
        assertEquals("FINANCE", result.category());
        assertEquals("FIXED", result.expenseType());
    }

    @Test
    void loanDisbursementIsNonConsumption() {
        TransactionAnalysisResult result = service.classify(
                target(
                        "LOAN",
                        "대출 실행",
                        null
                )
        ).orElseThrow();

        assertFalse(result.isConsumption());
        assertNull(result.category());
        assertNull(result.expenseType());
    }

    @Test
    void installmentPaymentIsFixedFinanceConsumption() {
        TransactionAnalysisResult result = service.classify(
                target(
                        "INSTALLMENT",
                        null,
                        null
                )
        ).orElseThrow();

        assertTrue(result.isConsumption());
        assertEquals("FINANCE", result.category());
        assertEquals("FIXED", result.expenseType());
    }

    @Test
    void clearFinancialTransferIsNonConsumption() {
        TransactionAnalysisResult result = service.classify(
                target(
                        "ORDINARY",
                        null,
                        "정기적금"
                )
        ).orElseThrow();

        assertFalse(result.isConsumption());
        assertNull(result.category());
        assertNull(result.expenseType());
    }

    @Test
    void genericTransactionChannelIsNotEnoughForNonConsumption() {
        Optional<TransactionAnalysisResult> result = service.classify(
                target(
                        "ORDINARY",
                        null,
                        "자동이체"
                )
        );

        /*
         * Optional.empty()는 Spring에서 소비 여부를 확정하지 않고
         * FastAPI로 전달해야 한다는 의미입니다.
         */
        assertTrue(result.isEmpty());
    }

    @Test
    void cardBillIsVariableEtcConsumption() {
        assertConsumptionDecision(
                "카드이용대금",
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void cashWithdrawalIsVariableEtcConsumption() {
        assertConsumptionDecision(
                "ATM출금",
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void insuranceIsFixedInsuranceConsumption() {
        assertConsumptionDecision(
                "삼성생명 실손보험",
                "INSURANCE",
                "FIXED"
        );
    }

    private void assertConsumptionDecision(
            String description,
            String expectedCategory,
            String expectedExpenseType
    ) {
        TransactionAnalysisResult result = service.classify(
                target(
                        "ORDINARY",
                        null,
                        description
                )
        ).orElseThrow();

        assertTrue(result.isConsumption());
        assertEquals(
                expectedCategory,
                result.category()
        );
        assertEquals(
                expectedExpenseType,
                result.expenseType()
        );
    }

    private ClassificationTargetTransaction target(
            String transactionCategory,
            String loanTransactionTypeName,
            String desc3
    ) {
        return new ClassificationTargetTransaction(
                1L,
                10L,
                transactionCategory,
                10_000L,
                10_000L,
                "0004",
                loanTransactionTypeName,
                null,
                null,
                desc3,
                null
        );
    }
}
