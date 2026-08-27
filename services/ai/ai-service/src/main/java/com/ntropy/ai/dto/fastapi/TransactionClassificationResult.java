package com.ntropy.ai.dto.fastapi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI가 반환하는 거래별 소비 분류 결과 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionClassificationResult {

    private Long transactionId;
    private Boolean isConsumption;
    private String category;
    private String expenseType;
}