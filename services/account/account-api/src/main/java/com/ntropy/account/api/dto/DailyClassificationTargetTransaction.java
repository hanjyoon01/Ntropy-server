package com.ntropy.account.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일간 소비 분류 배치가 처리할 ACCOUNT_TRANSACTION 원본 거래입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DailyClassificationTargetTransaction {

    private Long transactionId;
    private Long userId;
    private String transactionCategory;
    private Long outAmount;
    private Long inAmount;
    private String organizationCode;
    private String loanTransactionTypeName;
    private String desc1;
    private String desc2;
    private String desc3;
    private String desc4;
}
