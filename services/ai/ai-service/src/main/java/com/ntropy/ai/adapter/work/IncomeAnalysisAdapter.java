package com.ntropy.ai.adapter.work;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.ai.port.work.IncomeAnalysisPort;
import com.ntropy.ai.port.work.JobFatigue;
import com.ntropy.ai.port.work.JobIncome;
import com.ntropy.ai.port.work.MonthlyIncomeAnalysis;
import com.ntropy.work.api.client.IncomeAnalysisQueryClient;
import com.ntropy.work.api.dto.summary.JobFatigueSummary;
import com.ntropy.work.api.dto.summary.JobIncomeSummary;
import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;

import lombok.RequiredArgsConstructor;

/** work-service가 발행한 IncomeAnalysisQueryClient를 ai의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class IncomeAnalysisAdapter implements IncomeAnalysisPort {

    private final IncomeAnalysisQueryClient incomeAnalysisQueryClient;

    @Override
    public Map<Long, MonthlyIncomeAnalysis> getMonthlyIncomeAnalysisBulk(List<Long> userIds, YearMonth yearMonth) {
        return incomeAnalysisQueryClient.getMonthlyIncomeAnalysisBulk(userIds, yearMonth).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toPort(entry.getValue())));
    }

    private static MonthlyIncomeAnalysis toPort(MonthlyIncomeAnalysisSummary summary) {
        if (summary == null) {
            return null;
        }
        List<JobIncome> jobIncomes = summary.getJobIncomes() == null ? List.of()
                : summary.getJobIncomes().stream().map(IncomeAnalysisAdapter::toJobIncome).toList();
        List<JobFatigue> fatigueSummaries = summary.getFatigueSummaries() == null ? List.of()
                : summary.getFatigueSummaries().stream().map(IncomeAnalysisAdapter::toJobFatigue).toList();
        return new MonthlyIncomeAnalysis(
                summary.getTotalIncome(),
                summary.getPreviousMonthIncome(),
                summary.getIncomeChangeAmount(),
                summary.getIncomeChangeRate(),
                summary.getIncomeVolatility(),
                jobIncomes,
                fatigueSummaries
        );
    }

    private static JobIncome toJobIncome(JobIncomeSummary summary) {
        return new JobIncome(summary.getJobId(), summary.getJobName(), summary.getIncomeAmount(),
                summary.getIncomeRatio());
    }

    private static JobFatigue toJobFatigue(JobFatigueSummary summary) {
        return new JobFatigue(summary.getJobId(), summary.getJobName(), summary.getWorkDays(),
                summary.getTotalWorkMinutes(), summary.getAverageFatigue(), summary.getLatestFatigue());
    }
}
