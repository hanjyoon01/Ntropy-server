package com.ntropy.diagnosis.port.work;

/**
 * diagnosis-service가 재무진단에 사용하는 사용자·연월별 소득분석 결과. work-service의
 * MonthlyIncomeAnalysisSummary와 필드 구성은 비슷하지만, diagnosis가 소유한 별개의 타입이다.
 */
public record MonthlyIncomeAnalysis(
        Long totalIncome,
        Long unmatchedIncome
) {
}
