package com.ntropy.ai.port.work;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** ai-service가 정의한, work-service의 월별 소득분석 벌크 조회 포트. */
public interface IncomeAnalysisPort {

    Map<Long, MonthlyIncomeAnalysis> getMonthlyIncomeAnalysisBulk(List<Long> userIds, YearMonth yearMonth);
}
