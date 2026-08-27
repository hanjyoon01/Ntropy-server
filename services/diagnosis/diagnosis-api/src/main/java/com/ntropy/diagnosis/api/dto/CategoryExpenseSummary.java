package com.ntropy.diagnosis.api.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소비 카테고리별 금액과 비율을 전달하는 DTO입니다.
 *
 * categoryExpenses 응답의 개별 항목으로 사용됩니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryExpenseSummary {

    /**
     * 소비 카테고리 코드입니다.
     *
     * 예: FOOD, TRANSPORTATION, SHOPPING
     */
    private String category;

    /**
     * 해당 카테고리의 소비 금액입니다.
     */
    private Long amount;

    /**
     * 전체 소비 금액 대비 카테고리 소비 비율입니다.
     *
     * 계산식:
     * amount / totalExpense
     *
     * totalExpense가 0이면 null입니다.
     */
    private BigDecimal ratio;
}
