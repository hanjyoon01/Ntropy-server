package com.ntropy.account.api.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자·연월별 소비 집계 결과입니다.
 *
 * 원천 거래 전체를 전달하지 않고,
 * AI 리포트에 필요한 집계 결과만 전달합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyExpenseSummary {

    /**
     * 집계 대상 사용자 ID입니다.
     */
    private Long userId;

    /**
     * 집계 기준 연월입니다.
     *
     * 예: 2026-07
     */
    private String yearMonth;

    /**
     * 해당 월의 총소비 금액입니다.
     *
     * TXN_ANALYSIS에서 소비로 분류된 ORDINARY·LOAN 거래의 out_amount와
     * INSTALLMENT 거래의 in_amount를 합산합니다.
     * TXN_ANALYSIS가 없거나 소비가 아닌 거래는 제외합니다.
     */
    private Long totalExpense;

    /**
     * 해당 월의 고정지출 합계입니다.
     *
     * TXN_ANALYSIS에서 소비로 분류되고 지출 유형이 FIXED인 거래를
     * 거래 유형별 월간 소비 금액 기준으로 합산합니다.
     */
    private Long fixedExpense;

    /**
     * 카테고리별 소비 금액입니다.
     *
     * 예:
     * FOOD -> 600000
     * TRANSPORTATION -> 300000
     */
    private Map<String, Long> categoryExpenses;
}
