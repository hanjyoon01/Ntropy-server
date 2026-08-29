package com.ntropy.bff.dto.ai;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 추천 금융 상품의 수치형 상세 정보입니다. */
@Getter
@AllArgsConstructor
public class ProductDetailsResponse {
    private final BigDecimal interestRate;
    private final Integer savingPeriod;
    private final Long maxMonthlyAmount;
}
