package com.ntropy.account.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
/** account-service가 가공하여 방어모드에 전달하는 금융 납입 예정 항목. */
public class FinancialCommitmentSummary {
    /** 가공 결과를 별도 저장하지 않는 경우 null 허용. */
    private Long commitmentId;

    /** 원본 계좌 또는 금융상품 식별자. 보험은 출금계좌를 대표값으로 특정할 수 없어 null. */
    private Long accountId;

    /** SAVING_PAYMENT, LOAN_REPAYMENT, INSURANCE_PREMIUM. */
    private String expenseType;
    private String productName;

    /** 대출의 현재 잔액이며 적금·보험은 null 허용. */
    private Long outstandingBalance;

    /** 다음 예상 납입액(대출은 원금+이자 총상환액). 산출할 수 없으면 null. */
    private Long expectedAmount;

    /** 대출의 다음 예상 상환 원금. 원금을 산출할 수 없거나 대출이 아니면 null. */
    private Long expectedPrincipalAmount;

    /** 대출의 다음 예상 상환 이자. 이자를 산출할 수 없거나 대출이 아니면 null. */
    private Long expectedInterestAmount;

    /** 다음 예상 납입일. 산출할 수 없으면 null. */
    private LocalDate nextPaymentDate;

    /** CONFIRMED, ESTIMATED, INSUFFICIENT. */
    private String amountStatus;

    /** CONFIRMED, ESTIMATED, INSUFFICIENT. */
    private String dateStatus;

    public FinancialCommitmentSummary(
            Long commitmentId, Long accountId, String expenseType, String productName,
            Long outstandingBalance, Long expectedAmount, Long expectedPrincipalAmount, Long expectedInterestAmount,
            LocalDate nextPaymentDate, String amountStatus, String dateStatus) {
        this.commitmentId = commitmentId;
        this.accountId = accountId;
        this.expenseType = expenseType;
        this.productName = productName;
        this.outstandingBalance = outstandingBalance;
        this.expectedAmount = expectedAmount;
        this.expectedPrincipalAmount = expectedPrincipalAmount;
        this.expectedInterestAmount = expectedInterestAmount;
        this.nextPaymentDate = nextPaymentDate;
        this.amountStatus = amountStatus;
        this.dateStatus = dateStatus;
    }

    /** 원금·이자 분리 이전 호출 코드 호환용. expectedPrincipalAmount·expectedInterestAmount는 null로 채운다. */
    public FinancialCommitmentSummary(
            Long commitmentId, Long accountId, String expenseType, String productName,
            Long outstandingBalance, Long expectedAmount,
            LocalDate nextPaymentDate, String amountStatus, String dateStatus) {
        this(commitmentId, accountId, expenseType, productName, outstandingBalance, expectedAmount,
                null, null, nextPaymentDate, amountStatus, dateStatus);
    }
}
