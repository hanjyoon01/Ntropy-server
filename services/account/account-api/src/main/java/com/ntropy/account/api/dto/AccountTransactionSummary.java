package com.ntropy.account.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 외부 조회 계약에 노출하는 계좌 거래내역. 내부 중복 판별용 fingerprint는 포함하지 않는다. */
public record AccountTransactionSummary(
        Long transactionId,
        Long accountId,
        String transactionCategory,
        LocalDate transactionDate,
        LocalTime transactionTime,
        BigDecimal outAmount,
        BigDecimal inAmount,
        String loanTransactionTypeName,
        BigDecimal loanPrincipalAmount,
        BigDecimal loanInterestAmount,
        BigDecimal afterBalance,
        String desc1,
        String desc2,
        String desc3,
        String desc4
) {
}
