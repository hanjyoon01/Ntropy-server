package com.ntropy.ai.port.work;

import java.util.List;

/**
 * ai-service가 월간 리포트 생성에 사용하는 회원·연월별 소득분석 결과. work-service의
 * MonthlyIncomeAnalysisSummary 중 ai가 실제로 쓰는 필드만 추린 별개의 타입이다.
 */
public record MonthlyIncomeAnalysis(
        Long totalIncome,
        Long previousMonthIncome,
        Long incomeChangeAmount,
        Double incomeChangeRate,
        Double incomeVolatility,
        List<JobIncome> jobIncomes,
        List<JobFatigue> fatigueSummaries
) {
}
