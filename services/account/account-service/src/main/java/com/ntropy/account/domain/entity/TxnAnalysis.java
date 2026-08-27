package com.ntropy.account.domain.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TXN_ANALYSIS 테이블과 매핑되는 도메인 객체입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TxnAnalysis {

    private Long analysisId;
    private Long accountTransactionId;
    private Boolean isConsumption;
    private String category;
    private String expenseType;
    private LocalDateTime classifiedAt;
}