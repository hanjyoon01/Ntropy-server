package com.ntropy.account.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SQL에서 조회한 카테고리별 소비 금액 한 건입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryExpenseAmount {

    /**
     * 소비 카테고리입니다.
     */
    private String category;

    /**
     * 해당 카테고리의 소비 금액입니다.
     */
    private Long expenseAmount;
}
