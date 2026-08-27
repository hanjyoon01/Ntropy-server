package com.ntropy.diagnosis.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 종합분석 생성용 회원·연월 재무진단 상세입니다.
 *
 * 스칼라 필드는 저장된 {@code DIAGNOSIS_RESULT} 값을 그대로 반환하지만,
 * {@code categoryExpenses}는 조회 시점에 다시 집계한 값입니다. createdAt/updatedAt은
 * 조회 계약에서 사용하지 않으므로 포함하지 않습니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisAnalysisSummary {

    private Long diagnosisId;

    private Long userId;

    private String yearMonth;

    private Long totalIncome;

    private Long unmatchedIncome;

    private Long totalExpense;

    private Long netCashFlow;

    private Long fixedExpense;

    private BigDecimal fixedExpenseRatio;

    private Long totalFinancialAssets;

    private Long liquidAssets;

    private Long safeAssets;

    /** 조회 시점에 다시 집계한 카테고리별 소비 breakdown. */
    private List<CategoryExpenseSummary> categoryExpenses;

    private LocalDateTime calculatedAt;

    /** 월말 기준으로 확정된 시각. 진행 중인 현재 월은 null이다. */
    private LocalDateTime finalizedAt;

    /** {@code finalizedAt != null}로부터 유도되는 파생 값. */
    private boolean finalized;
}
