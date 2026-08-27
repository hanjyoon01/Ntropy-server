package com.ntropy.diagnosis.client;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.MonthlyExpenseQueryClient;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.api.client.DiagnosisAnalysisQueryClient;
import com.ntropy.diagnosis.api.dto.CategoryExpenseSummary;
import com.ntropy.diagnosis.api.dto.DiagnosisAnalysisSummary;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.service.CategoryExpenseCalculator;
import com.ntropy.diagnosis.service.DiagnosisResultService;

import lombok.RequiredArgsConstructor;

/**
 * DiagnosisAnalysisQueryClient의 diagnosis-service 내부 구현체입니다.
 *
 * <p>저장된 DIAGNOSIS_RESULT 스칼라 값에, 조회 시점에 다시 집계한 카테고리별 소비
 * breakdown을 더해 반환합니다. AI-service는 recalculate() 성공 직후에만 이 계약을
 * 호출해야 합니다 - 확정된 과거월을 나중에 다시 조회하면 스칼라(고정)와 카테고리
 * breakdown(실시간 재집계)의 기준 시점이 어긋날 수 있습니다.</p>
 */
@Component
@RequiredArgsConstructor
public class LocalDiagnosisAnalysisQueryClient implements DiagnosisAnalysisQueryClient {

    private final DiagnosisResultService diagnosisResultService;
    private final MonthlyExpenseQueryClient monthlyExpenseQueryClient;
    private final CategoryExpenseCalculator categoryExpenseCalculator;

    @Override
    public DiagnosisAnalysisSummary getMonthlyAnalysis(Long userId, YearMonth yearMonth) {
        // 진단 결과가 없으면 기존 조회 계약과 동일하게 DIAGNOSIS_RESULT_NOT_FOUND(404)를 던진다.
        DiagnosisResult result = diagnosisResultService.findByUserIdAndYearMonth(userId, yearMonth.toString());

        MonthlyExpenseSummary monthlyExpense =
                monthlyExpenseQueryClient.findMonthlyExpense(userId, yearMonth.toString());
        if (monthlyExpense == null) {
            throw new ServiceException(DiagnosisErrorCode.INVALID_CALCULATION_INPUT, "월별 소비 조회 결과가 없습니다.");
        }
        List<CategoryExpenseSummary> categoryExpenses = categoryExpenseCalculator.calculate(
                monthlyExpense.getCategoryExpenses(), monthlyExpense.getTotalExpense());

        return new DiagnosisAnalysisSummary(
                result.getDiagnosisId(),
                result.getUserId(),
                result.getYearMonth(),
                result.getTotalIncome(),
                result.getUnmatchedIncome(),
                result.getTotalExpense(),
                result.getNetCashFlow(),
                result.getFixedExpense(),
                result.getFixedExpenseRatio(),
                result.getTotalFinancialAssets(),
                result.getLiquidAssets(),
                result.getSafeAssets(),
                categoryExpenses,
                result.getCalculatedAt(),
                result.getFinalizedAt(),
                result.getFinalizedAt() != null
        );
    }
}
