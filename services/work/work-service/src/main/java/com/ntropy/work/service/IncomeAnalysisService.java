package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.work.api.dto.summary.EarnedDepositComparison;
import com.ntropy.work.api.dto.summary.JobFatigueSummary;
import com.ntropy.work.api.dto.summary.JobIncomeSummary;
import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.SettlementMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;

/**
 * 회원·연월별 소득분석(재무진단 연계 §1~14 기준)을 계산한다.
 *
 * <p>SettlementService가 배치로 미리 만들어 둔 SETTLEMENT 행(MATCHED/UNMATCHED, deposit_date
 * 기준)을 그대로 집계한다. 한 플랫폼에 회원 잡이 여러 개 매핑되는 경우(AMBIGUOUS)는 없다고
 * 가정하고 다루지 않으며, ambiguousTransactionCount는 항상 0으로 내려간다.</p>
 *
 * <p>변동성(§9)만 예외적으로 특정 과거 월 조회가 실패해도 그 달을 제외하고 계산한다
 * (2개월 미만이면 null). 그 외 totalIncome/previousMonthIncome 등 핵심 지표는 실패를
 * 감추지 않고 예외를 그대로 전파한다(§12) — diagnosis-service는 이 경우 기존 정상
 * DIAGNOSIS_RESULT를 유지한다.</p>
 *
 * <p>getMonthlyIncomeAnalysis(단건)와 getMonthlyIncomeAnalysisBulk(AI 리포트 배치 전용)는
 * buildSummary()로 요약 조립 로직을 공유한다. 단, previousMonthIncome/twoMonthsAgoIncome의
 * null 의미가 경로마다 다르다: 단건 경로는 사용자별로 조회를 격리해서 실패 시 null(§8)을
 * 내려주지만, 벌크 경로는 여러 사용자를 한 쿼리로 묶어 조회하기 때문에 사용자 단위 실패
 * 격리가 없다 - 데이터가 없으면 0L이고, 조회 자체가 실패하면 예외가 호출부(배치)로 그대로
 * 전파된다.</p>
 */
@Service
@RequiredArgsConstructor
public class IncomeAnalysisService {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final int VOLATILITY_MIN_VALID_MONTHS = 2;

    private final SettlementMapper settlementMapper;
    private final JobMapper jobMapper;
    private final WorkLogMapper workLogMapper;
    private final WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;

    public MonthlyIncomeAnalysisSummary getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth) {
        Map<Long, String> jobNames = jobMapper.findByUserId(userId).stream()
                .collect(Collectors.toMap(Job::getJobId, Job::getJobName, (a, b) -> a));

        // 이번 달 본체 조회/계산 실패는 그대로 전파한다 (§12) - diagnosis-service가 기존 스냅샷을 유지하도록.
        MonthAggregate current = aggregateMonth(userId, yearMonth);

        // 전월/전전월 조회 실패는 이번 달 분석 전체를 실패시키지 않고 null로 구분한다 (§8).
        // previousMonthIncome=null: 조회 실패, previousMonthIncome=0L: 실제 전월 소득 0원.
        Long previousMonthIncome = fetchTotalIncomeSafely(userId, yearMonth.minusMonths(1));
        Long twoMonthsAgoIncome = fetchTotalIncomeSafely(userId, yearMonth.minusMonths(2));
        Long pendingSettlementIncome = calculatePendingSettlementIncome(userId, yearMonth);
        List<WorkLog> workLogsInMonth = findWorkLogsInMonth(userId, yearMonth);

        return buildSummary(userId, yearMonth, jobNames, current, previousMonthIncome, twoMonthsAgoIncome,
                pendingSettlementIncome, workLogsInMonth);
    }

    /**
     * AI 리포트 배치 전용 벌크 조회: 여러 사용자의 소득분석을 쿼리 수를 사용자 수와
     * 무관하게 고정된 개수로 유지하면서 한 번에 계산한다 (SettlementMapper 3회 -
     * 이번달/전월/전전월 -, JobMapper 1회, WorkLogMapper 1회, WorkLogPlatformIncomeMapper
     * 1회, 총 6쿼리).
     *
     * <p>userIds에 없는 사용자는 결과 Map에도 없다. 어떤 userId가 결과에 데이터가
     * 없더라도(잡/정산 이력이 없는 신규 사용자 등) 빈 값으로 채워진 Summary가 들어간다 -
     * 단건 경로와 달리 사용자 단위로 조회를 건너뛰지 않는다.</p>
     */
    public Map<Long, MonthlyIncomeAnalysisSummary> getMonthlyIncomeAnalysisBulk(
            List<Long> userIds, YearMonth yearMonth) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<Long, String>> jobNamesByUser = jobMapper.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(Job::getUserId,
                        Collectors.toMap(Job::getJobId, Job::getJobName, (a, b) -> a)));

        Map<Long, MonthAggregate> currentByUser = aggregateMonthBulk(userIds, yearMonth);
        Map<Long, MonthAggregate> previousByUser = aggregateMonthBulk(userIds, yearMonth.minusMonths(1));
        Map<Long, MonthAggregate> twoMonthsAgoByUser = aggregateMonthBulk(userIds, yearMonth.minusMonths(2));

        List<WorkLog> workLogs = workLogMapper.findByUserIdInAndDateRange(
                userIds, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        Map<Long, List<WorkLog>> workLogsByUser = workLogs.stream()
                .collect(Collectors.groupingBy(WorkLog::getUserId, LinkedHashMap::new, Collectors.toList()));

        // WorkLogPlatformIncome에는 userId가 없어서, 같은 기간에 조회한 WorkLog(logId → userId)로
        // 매핑해야 한다 (WorkLogPlatformIncomeMapper 컨벤션 참고).
        Map<Long, Long> userIdByLogId = workLogs.stream()
                .collect(Collectors.toMap(WorkLog::getLogId, WorkLog::getUserId, (a, b) -> a));
        Map<Long, Long> pendingSettlementIncomeByUser =
                calculatePendingSettlementIncomeBulk(userIds, yearMonth, userIdByLogId);

        Map<Long, MonthlyIncomeAnalysisSummary> result = new LinkedHashMap<>();
        for (Long userId : userIds) {
            Map<Long, String> jobNames = jobNamesByUser.getOrDefault(userId, Map.of());
            MonthAggregate current = currentByUser.getOrDefault(userId, new MonthAggregate());
            Long previousMonthIncome = previousByUser.getOrDefault(userId, new MonthAggregate()).totalIncome();
            Long twoMonthsAgoIncome = twoMonthsAgoByUser.getOrDefault(userId, new MonthAggregate()).totalIncome();
            Long pendingSettlementIncome = pendingSettlementIncomeByUser.getOrDefault(userId, 0L);
            List<WorkLog> workLogsInMonth = workLogsByUser.getOrDefault(userId, List.of());

            result.put(userId, buildSummary(userId, yearMonth, jobNames, current,
                    previousMonthIncome, twoMonthsAgoIncome, pendingSettlementIncome, workLogsInMonth));
        }
        return result;
    }

    /**
     * 이미 조회된 재료로 MonthlyIncomeAnalysisSummary를 조립한다.
     * getMonthlyIncomeAnalysis(단건)와 getMonthlyIncomeAnalysisBulk(벌크)가 공유하는
     * 유일한 조립 지점 - 두 경로의 계산 로직이 divergence 없이 항상 같은 결과를 내도록 한다.
     */
    private MonthlyIncomeAnalysisSummary buildSummary(
            Long userId,
            YearMonth yearMonth,
            Map<Long, String> jobNames,
            MonthAggregate current,
            Long previousMonthIncome,
            Long twoMonthsAgoIncome,
            Long pendingSettlementIncome,
            List<WorkLog> workLogsInMonth
    ) {
        Long changeAmount = previousMonthIncome == null ? null : current.totalIncome() - previousMonthIncome;
        Double changeRate = (previousMonthIncome == null || previousMonthIncome == 0)
                ? null
                : (double) changeAmount / previousMonthIncome;

        return MonthlyIncomeAnalysisSummary.builder()
                .userId(userId)
                .yearMonth(yearMonth)
                .asOfDate(resolveAsOfDate(yearMonth))
                .totalIncome(current.totalIncome())
                .unmatchedIncome(current.unmatchedIncome())
                .pendingSettlementIncome(pendingSettlementIncome)
                .matchedTransactionCount(current.matchedCount())
                .unmatchedTransactionCount(current.unmatchedCount())
                .ambiguousTransactionCount(0)
                .jobIncomes(buildJobIncomes(current, jobNames))
                .primaryJobId(resolvePrimaryJobId(current))
                .primaryJobName(resolvePrimaryJobName(current, jobNames))
                .previousMonthIncome(previousMonthIncome)
                .incomeChangeAmount(changeAmount)
                .incomeChangeRate(changeRate)
                .incomeVolatility(calculateVolatility(current.totalIncome(), previousMonthIncome, twoMonthsAgoIncome))
                .earnedDepositComparisons(buildEarnedDepositComparisons(workLogsInMonth, current, jobNames))
                .fatigueSummaries(buildFatigueSummaries(workLogsInMonth, jobNames))
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    /** 조회/계산 실패 시 null을 반환해 "실패"와 "실제 0원"을 구분한다. 단건 경로 전용. */
    private Long fetchTotalIncomeSafely(Long userId, YearMonth yearMonth) {
        try {
            return aggregateMonth(userId, yearMonth).totalIncome();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private LocalDate resolveAsOfDate(YearMonth yearMonth) {
        return yearMonth.equals(YearMonth.now()) ? LocalDate.now() : yearMonth.atEndOfMonth();
    }

    // ---------- SETTLEMENT 집계 ----------

    private MonthAggregate aggregateMonth(Long userId, YearMonth yearMonth) {
        List<Settlement> settlements = settlementMapper.findByUserIdAndDepositDateRange(
                userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return aggregate(settlements);
    }

    /** userIds 전원의 SETTLEMENT를 한 쿼리로 조회해 사용자별로 집계한다. 벌크 경로 전용. */
    private Map<Long, MonthAggregate> aggregateMonthBulk(List<Long> userIds, YearMonth yearMonth) {
        List<Settlement> settlements = settlementMapper.findByUserIdInAndDepositDateRange(
                userIds, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        Map<Long, List<Settlement>> byUser = settlements.stream()
                .collect(Collectors.groupingBy(Settlement::getUserId));

        Map<Long, MonthAggregate> result = new LinkedHashMap<>();
        for (Long userId : userIds) {
            result.put(userId, aggregate(byUser.getOrDefault(userId, List.of())));
        }
        return result;
    }

    private MonthAggregate aggregate(List<Settlement> settlements) {
        MonthAggregate aggregate = new MonthAggregate();
        for (Settlement settlement : settlements) {
            aggregate.apply(settlement);
        }
        return aggregate;
    }

    /**
     * 확정(CONFIRMED)됐지만 아직 COMPLETED가 아닌 WORK_LOG_PLATFORM_INCOME 행들의 합계.
     * 근무일지 전체가 아니라 income 행 단위로 보는 이유: 여러 플랫폼을 동시에 뛴 근무일지는
     * 일부 플랫폼만 정산 완료(PARTIAL)될 수 있어서, 아직 안 들어온 플랫폼 몫만 집계해야
     * 정확하다. 매핑된 플랫폼이 없는 잡의 근무일지(income 행 자체가 없음)는 이 집계에서
     * 빠진다 - 알려진 한계. 단건 경로 전용.
     */
    private Long calculatePendingSettlementIncome(Long userId, YearMonth yearMonth) {
        return workLogPlatformIncomeMapper.findConfirmedByUserIdAndDateRange(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth())
                .stream()
                .filter(income -> income.getSettlementStatus() != SettlementStatus.COMPLETED)
                .mapToLong(income -> income.getExpectedAmount() == null ? 0L : income.getExpectedAmount())
                .sum();
    }

    /**
     * calculatePendingSettlementIncome의 벌크 버전. WorkLogPlatformIncome에는 userId가
     * 없어서 호출부가 넘겨준 userIdByLogId(같은 기간 WorkLog에서 뽑은 매핑)로 사용자를 구분한다.
     */
    private Map<Long, Long> calculatePendingSettlementIncomeBulk(
            List<Long> userIds, YearMonth yearMonth, Map<Long, Long> userIdByLogId) {
        List<WorkLogPlatformIncome> incomes = workLogPlatformIncomeMapper.findConfirmedByUserIdInAndDateRange(
                userIds, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long userId : userIds) {
            result.put(userId, 0L);
        }
        for (WorkLogPlatformIncome income : incomes) {
            if (income.getSettlementStatus() == SettlementStatus.COMPLETED) {
                continue;
            }
            Long userId = userIdByLogId.get(income.getLogId());
            if (userId == null) {
                continue;
            }
            long amount = income.getExpectedAmount() == null ? 0L : income.getExpectedAmount();
            result.merge(userId, amount, Long::sum);
        }
        return result;
    }

    // ---------- 잡별 소득 (§7) ----------

    private List<JobIncomeSummary> buildJobIncomes(MonthAggregate current, Map<Long, String> jobNames) {
        List<JobIncomeSummary> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : current.incomeByJob().entrySet()) {
            Long jobId = entry.getKey();
            long amount = entry.getValue();
            double ratio = current.totalIncome() == 0 ? 0 : (double) amount / current.totalIncome();
            int count = current.transactionCountByJob().getOrDefault(jobId, 0);
            result.add(new JobIncomeSummary(jobId, jobNames.get(jobId), amount, ratio, count));
        }
        return result;
    }

    /** 소득금액이 가장 큰 잡이 여러 개로 동률이면 임의로 고르지 않고 null을 반환한다(§7). */
    private Long resolvePrimaryJobId(MonthAggregate current) {
        List<Long> topJobs = findTopIncomeJobs(current);
        return topJobs.size() == 1 ? topJobs.get(0) : null;
    }

    private String resolvePrimaryJobName(MonthAggregate current, Map<Long, String> jobNames) {
        Long primaryJobId = resolvePrimaryJobId(current);
        return primaryJobId == null ? null : jobNames.get(primaryJobId);
    }

    private List<Long> findTopIncomeJobs(MonthAggregate current) {
        Map<Long, Long> incomeByJob = current.incomeByJob();
        if (incomeByJob.isEmpty()) {
            return List.of();
        }
        long maxAmount = incomeByJob.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        return incomeByJob.entrySet().stream()
                .filter(entry -> entry.getValue() == maxAmount)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ---------- 소득 변동성 (§9) ----------

    /**
     * 대상 월을 포함한 최근 최대 3개월의 변동계수. 조회 실패(null)한 달은 제외하며,
     * 유효한 월이 2개 미만이거나 평균소득이 0이면 null.
     */
    private Double calculateVolatility(long currentMonthIncome, Long previousMonthIncome, Long twoMonthsAgoIncome) {
        List<Long> monthlyIncomes = new ArrayList<>();
        monthlyIncomes.add(currentMonthIncome);
        if (previousMonthIncome != null) {
            monthlyIncomes.add(previousMonthIncome);
        }
        if (twoMonthsAgoIncome != null) {
            monthlyIncomes.add(twoMonthsAgoIncome);
        }

        if (monthlyIncomes.size() < VOLATILITY_MIN_VALID_MONTHS) {
            return null;
        }
        double mean = monthlyIncomes.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean == 0) {
            return null;
        }
        double variance = monthlyIncomes.stream()
                .mapToDouble(income -> Math.pow(income - mean, 2))
                .sum() / monthlyIncomes.size();
        return Math.sqrt(variance) / mean;
    }

    // ---------- 발생소득 vs 실입금소득 (§10) ----------

    private List<EarnedDepositComparison> buildEarnedDepositComparisons(
            List<WorkLog> workLogsInMonth, MonthAggregate current, Map<Long, String> jobNames) {
        Map<Long, Long> earnedByJob = new LinkedHashMap<>();
        for (WorkLog workLog : workLogsInMonth) {
            if (!STATUS_CONFIRMED.equals(workLog.getStatus())) {
                continue;
            }
            long income = workLog.getEstimatedIncome() == null ? 0 : workLog.getEstimatedIncome();
            earnedByJob.merge(workLog.getJobId(), income, Long::sum);
        }

        Set<Long> jobIds = new LinkedHashSet<>();
        jobIds.addAll(earnedByJob.keySet());
        jobIds.addAll(current.incomeByJob().keySet());

        List<EarnedDepositComparison> result = new ArrayList<>();
        for (Long jobId : jobIds) {
            long earned = earnedByJob.getOrDefault(jobId, 0L);
            long deposited = current.incomeByJob().getOrDefault(jobId, 0L);
            result.add(new EarnedDepositComparison(jobId, jobNames.get(jobId), earned, deposited, deposited - earned));
        }
        return result;
    }

    // ---------- 잡별 피로도 (§11) ----------

    /**
     * WorkLog.fatigue는 등록 시점(WorkLogService)에 이미 계획=기본피로도/확정=실제피로도로
     * 채워져 있으므로, 완료/미래 일정을 여기서 다시 구분하지 않고 그대로 가중평균한다.
     */
    private List<JobFatigueSummary> buildFatigueSummaries(List<WorkLog> workLogsInMonth, Map<Long, String> jobNames) {
        Map<Long, List<WorkLog>> byJob = workLogsInMonth.stream()
                .collect(Collectors.groupingBy(WorkLog::getJobId, LinkedHashMap::new, Collectors.toList()));

        List<JobFatigueSummary> result = new ArrayList<>();
        for (Map.Entry<Long, List<WorkLog>> entry : byJob.entrySet()) {
            Long jobId = entry.getKey();
            List<WorkLog> jobLogs = entry.getValue();

            int workDays = (int) jobLogs.stream().map(WorkLog::getWorkDate).distinct().count();
            long totalMinutes = jobLogs.stream()
                    .mapToLong(log -> WorkTimeUtils.durationMinutes(log.getStartTime(), log.getEndTime()))
                    .sum();
            Double averageFatigue = totalMinutes == 0 ? null : jobLogs.stream()
                    .mapToDouble(log -> log.getFatigue() * WorkTimeUtils.durationMinutes(log.getStartTime(), log.getEndTime()))
                    .sum() / totalMinutes;
            Long latestFatigue = jobLogs.stream()
                    .max(Comparator.comparing(WorkLog::getWorkDate))
                    .map(WorkLog::getFatigue)
                    .orElse(null);

            result.add(new JobFatigueSummary(jobId, jobNames.get(jobId), workDays, totalMinutes, averageFatigue, latestFatigue));
        }
        return result;
    }

    private List<WorkLog> findWorkLogsInMonth(Long userId, YearMonth yearMonth) {
        return workLogMapper.findByUserIdAndDateRange(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    // ---------- 월 집계 값객체 ----------

    private static final class MonthAggregate {
        private long totalIncome = 0;
        private long unmatchedIncome = 0;
        private int matchedCount = 0;
        private int unmatchedCount = 0;
        private final Map<Long, Long> incomeByJob = new LinkedHashMap<>();
        private final Map<Long, Integer> transactionCountByJob = new LinkedHashMap<>();

        private void apply(Settlement settlement) {
            long amount = settlement.getActualAmount();
            int count = settlement.getTransactionCount() == null ? 1 : settlement.getTransactionCount();
            if (settlement.getStatus() == SettlementMatchStatus.MATCHED) {
                totalIncome += amount;
                matchedCount += count;
                incomeByJob.merge(settlement.getJobId(), amount, Long::sum);
                transactionCountByJob.merge(settlement.getJobId(), count, Integer::sum);
            } else {
                unmatchedIncome += amount;
                unmatchedCount += count;
            }
        }

        private long totalIncome() {
            return totalIncome;
        }

        private long unmatchedIncome() {
            return unmatchedIncome;
        }

        private int matchedCount() {
            return matchedCount;
        }

        private int unmatchedCount() {
            return unmatchedCount;
        }

        private Map<Long, Long> incomeByJob() {
            return incomeByJob;
        }

        private Map<Long, Integer> transactionCountByJob() {
            return transactionCountByJob;
        }
    }
}
