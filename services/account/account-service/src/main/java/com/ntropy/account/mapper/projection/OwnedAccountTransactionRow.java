package com.ntropy.account.mapper.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.ntropy.account.domain.AccountTransactionCategory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 계좌 소유권과 거래내역을 한 번의 조인 쿼리로 조회한 결과 행. */
@Getter
@Setter
@NoArgsConstructor
public class OwnedAccountTransactionRow {

    private Long ownedAccountId;
    private Long transactionId;
    private AccountTransactionCategory transactionCategory;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private BigDecimal outAmount;
    private BigDecimal inAmount;
    private String loanTransactionTypeName;
    private BigDecimal loanPrincipalAmount;
    private BigDecimal loanInterestAmount;
    private BigDecimal afterBalance;
    private String desc1;
    private String desc2;
    private String desc3;
    private String desc4;
}
