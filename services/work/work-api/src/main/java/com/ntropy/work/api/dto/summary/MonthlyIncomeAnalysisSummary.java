package com.ntropy.work.api.dto.summary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * work-service가 diagnosis-service/AI-service에 제공하는 회원·연월별 소득분석 결과.
 * 필드가 많아 다른 summary DTO와 달리 Builder를 사용한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyIncomeAnalysisSummary {

    private Long userId;
    private YearMonth yearMonth;
    private LocalDate asOfDate;
    private Long totalIncome;
    private Long unmatchedIncome;
    private Long pendingSettlementIncome;
    private Integer matchedTransactionCount;
    private Integer unmatchedTransactionCount;
    private Integer ambiguousTransactionCount;
    private List<JobIncomeSummary> jobIncomes;
    private Long primaryJobId;
    private String primaryJobName;
    private Long previousMonthIncome;
    private Long incomeChangeAmount;
    private Double incomeChangeRate;
    private Double incomeVolatility;
    private List<EarnedDepositComparison> earnedDepositComparisons;
    private List<JobFatigueSummary> fatigueSummaries;
    private LocalDateTime calculatedAt;
}
