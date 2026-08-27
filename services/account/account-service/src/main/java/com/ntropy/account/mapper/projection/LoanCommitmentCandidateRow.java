package com.ntropy.account.mapper.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 대출 계좌 1건과 가장 최근 정상 상환 LOAN 거래(있으면)를 함께 담은 조회 행. */
@Getter
@Setter
public class LoanCommitmentCandidateRow {
    private Long accountId;
    private String productName;
    private BigDecimal outstandingBalance;
    private LocalDate nextPaymentDate;

    /** 최근 정상 상환 거래의 총상환액(out_amount). */
    private BigDecimal expectedAmount;

    /** 최근 정상 상환 거래의 원금(loan_principal_amount). */
    private BigDecimal expectedPrincipalAmount;

    /** 최근 정상 상환 거래의 이자(loan_interest_amount). */
    private BigDecimal expectedInterestAmount;
}
