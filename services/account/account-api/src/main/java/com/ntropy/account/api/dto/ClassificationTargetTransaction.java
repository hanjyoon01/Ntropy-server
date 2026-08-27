package com.ntropy.account.api.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI 소비 분류 대상 거래 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationTargetTransaction {

    private Long transactionId;
    private String merchantName;
    private String description;
    private Long amount;
    private LocalDateTime transactionDate;
}
