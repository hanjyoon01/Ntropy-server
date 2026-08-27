package com.ntropy.diagnosis.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월별 재무진단 결과를 모듈 간에 전달하는 DTO입니다.
 *
 * diagnosis-service가 defense-service·bff-service에 조회 결과를
 * 전달할 때 사용합니다. createdAt/updatedAt은 조회 계약에서
 * 사용하지 않으므로 포함하지 않습니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResultSummary {

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

    private LocalDateTime calculatedAt;
}
