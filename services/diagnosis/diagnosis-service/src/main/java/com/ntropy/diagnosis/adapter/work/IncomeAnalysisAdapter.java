package com.ntropy.diagnosis.adapter.work;

import java.time.YearMonth;

import org.springframework.stereotype.Component;

import com.ntropy.diagnosis.port.work.MonthlyIncomeAnalysis;
import com.ntropy.diagnosis.port.work.IncomeAnalysisPort;
import com.ntropy.work.api.client.IncomeAnalysisQueryClient;
import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;

import lombok.RequiredArgsConstructor;

/** work-service가 발행한 IncomeAnalysisQueryClient를 diagnosis의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class IncomeAnalysisAdapter implements IncomeAnalysisPort {

    private final IncomeAnalysisQueryClient incomeAnalysisQueryClient;

    @Override
    public MonthlyIncomeAnalysis getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth) {
        MonthlyIncomeAnalysisSummary summary = incomeAnalysisQueryClient.getMonthlyIncomeAnalysis(userId, yearMonth);
        if (summary == null) {
            return null;
        }
        return new MonthlyIncomeAnalysis(summary.getTotalIncome(), summary.getUnmatchedIncome());
    }
}
