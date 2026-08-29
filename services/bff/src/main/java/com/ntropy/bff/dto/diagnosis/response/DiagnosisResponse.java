package com.ntropy.bff.dto.diagnosis.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ntropy.diagnosis.api.dto.DiagnosisResultSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프론트엔드에 반환할 월별 재무진단 결과 응답 DTO입니다.
 *
 * userId(인증 정보), createdAt, updatedAt은 응답에서 제외합니다.
 */
@Getter
@NoArgsConstructor
public class DiagnosisResponse {

    private Long diagnosisId;
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

    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss"
    )
    private LocalDateTime calculatedAt;

    public static DiagnosisResponse from(DiagnosisResultSummary summary) {
        DiagnosisResponse response = new DiagnosisResponse();

        response.diagnosisId = summary.getDiagnosisId();
        response.yearMonth = summary.getYearMonth();
        response.totalIncome = summary.getTotalIncome();
        response.unmatchedIncome = summary.getUnmatchedIncome();
        response.totalExpense = summary.getTotalExpense();
        response.netCashFlow = summary.getNetCashFlow();
        response.fixedExpense = summary.getFixedExpense();
        response.fixedExpenseRatio = summary.getFixedExpenseRatio();
        response.totalFinancialAssets = summary.getTotalFinancialAssets();
        response.liquidAssets = summary.getLiquidAssets();
        response.safeAssets = summary.getSafeAssets();
        response.calculatedAt = summary.getCalculatedAt();

        return response;
    }
}
