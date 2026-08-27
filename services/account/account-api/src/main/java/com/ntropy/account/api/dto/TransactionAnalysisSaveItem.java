package com.ntropy.account.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 1건의 소비 분류 저장 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAnalysisSaveItem {

    private Long transactionId;
    private Boolean isConsumption;
    private String category;
    private String expenseType;
}
