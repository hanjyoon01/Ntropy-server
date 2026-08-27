package com.ntropy.account.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.ntropy.account.domain.AccountTransactionCategory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CODEF 수시입출·적금·대출 거래내역 조회 응답의 거래 1건을 저장하는 도메인 객체.
 * desc1~desc4는 은행마다 의미가 달라 원본 필드명을 그대로 보존한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountTransaction {

    private Long id;
    private Long accountId;
    private String fingerprint;
    private AccountTransactionCategory transactionCategory;

    /** 대출 거래내역 반복부의 거래구분명(resTransTypeNm) 원문. 일반·적금 거래는 null. */
    private String loanTransactionTypeName;

    private LocalDate tranDate;
    private LocalTime tranTime;
    private BigDecimal outAmount;
    private BigDecimal inAmount;

    // 대출 거래내역 반복부 원금·이자. 일반·적금 거래는 null.
    private BigDecimal loanPrincipalAmount;
    private BigDecimal loanInterestAmount;

    private BigDecimal afterBalance;
    private String desc1;
    private String desc2;
    private String desc3;
    private String desc4;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
