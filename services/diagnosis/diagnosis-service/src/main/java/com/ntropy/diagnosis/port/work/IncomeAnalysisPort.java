package com.ntropy.diagnosis.port.work;

import java.time.YearMonth;

/** diagnosis-service가 정의한, work-service의 소득분석 조회 포트. */
@FunctionalInterface
public interface IncomeAnalysisPort {

    MonthlyIncomeAnalysis getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth);
}
