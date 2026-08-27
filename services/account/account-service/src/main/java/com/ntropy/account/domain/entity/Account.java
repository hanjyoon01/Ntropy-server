package com.ntropy.account.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CODEF 보유계좌 조회 응답 1건을 저장하는 도메인 객체.
 * 계좌 원문 번호는 저장하지 않고 표시용 마스킹 값과 중복 판별용 해시만 가진다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Account {

    private Long id;
    private Long codefConnectionId;
    private Long userId;
    /** CODEF_CONNECTION.provider 조회 결과. ACCOUNT 테이블에는 저장하지 않는다. */
    private transient String connectionProvider;
    private String organizationCode;
    private AccountGroup accountGroup;
    private String depositTypeCode;
    private String accountNoMasked;
    private String accountNoHash;
    private String accountName;
    private BigDecimal balance;

    /** 대출 거래내역 계좌 상세의 약정원금(resPrincipal). 대출 계좌 전용. */
    private BigDecimal loanContractPrincipal;

    /** 적금·대출 거래내역 계좌 상세의 이율(resRate). 보유계좌 응답에는 없어 미적용·응답 누락 시 null. */
    private BigDecimal interestRate;

    private String currencyCode;
    private LocalDate accountStartDate;

    /** 예금·적금·대출 만기일(resAccountEndDate). */
    private LocalDate maturityDate;

    private LocalDate lastTranDate;

    // 예금/신탁 그룹 전용
    private Boolean overdraftYn;

    // 다음 적금 납입일 또는 대출 상환일
    private LocalDate nextPaymentDate;

    private AccountStatus status;
    private LocalDateTime deactivatedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
