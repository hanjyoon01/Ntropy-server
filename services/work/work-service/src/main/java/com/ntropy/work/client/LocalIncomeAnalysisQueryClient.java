package com.ntropy.work.client;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.IncomeAnalysisQueryClient;
import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.work.service.IncomeAnalysisService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalIncomeAnalysisQueryClient implements IncomeAnalysisQueryClient {

    private final IncomeAnalysisService incomeAnalysisService;

    @Override
    public MonthlyIncomeAnalysisSummary getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth) {
        return incomeAnalysisService.getMonthlyIncomeAnalysis(userId, yearMonth);
    }

    @Override
    public Map<Long, MonthlyIncomeAnalysisSummary> getMonthlyIncomeAnalysisBulk(
            List<Long> userIds, YearMonth yearMonth) {
        return incomeAnalysisService.getMonthlyIncomeAnalysisBulk(userIds, yearMonth);
    }
}
