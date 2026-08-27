package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.api.dto.summary.EarnedDepositComparison;
import com.ntropy.work.api.dto.summary.JobFatigueSummary;
import com.ntropy.work.api.dto.summary.JobIncomeSummary;
import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;
import com.ntropy.work.mapper.InMemoryWorkLogPlatformIncomeMapper;
import com.ntropy.work.mapper.SettlementMapper;

class IncomeAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_A = 100L;
    private static final Long JOB_B = 200L;
    private static final Long PLATFORM_ID = 900L;
    private static final YearMonth TARGET = YearMonth.of(2026, 7);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDate PREVIOUS_MONTH_DATE = LocalDate.of(2026, 6, 10);

    private final InMemoryJobMapper jobMapper = new InMemoryJobMapper();
    private final InMemoryWorkLogMapper workLogMapper = new InMemoryWorkLogMapper();
    private final InMemoryWorkLogPlatformIncomeMapper workLogPlatformIncomeMapper =
            new InMemoryWorkLogPlatformIncomeMapper(workLogMapper);
    private StubSettlementMapper settlementMapper;
    private IncomeAnalysisService service;

    @BeforeEach
    void setUp() {
        settlementMapper = new StubSettlementMapper();
        service = new IncomeAnalysisService(settlementMapper, jobMapper, workLogMapper, workLogPlatformIncomeMapper);
    }

    @Test
    @DisplayName("MATCHED SETTLEMENT는 총소득과 잡별 소득에 반영된다")
    void matchedSettlement_addsToTotalIncomeAndJobIncome() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 50_000L, 1));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(50_000L, result.getTotalIncome());
        assertEquals(0L, result.getUnmatchedIncome());
        assertEquals(1, result.getMatchedTransactionCount());
        assertEquals(1, result.getJobIncomes().size());
        JobIncomeSummary jobIncome = result.getJobIncomes().get(0);
        assertEquals(JOB_A, jobIncome.getJobId());
        assertEquals(50_000L, jobIncome.getIncomeAmount());
        assertEquals(1.0, jobIncome.getIncomeRatio());
        assertEquals(JOB_A, result.getPrimaryJobId());
    }

    @Test
    @DisplayName("UNMATCHED SETTLEMENT(job_id=null)는 unmatchedIncome에만 합산되고 건수도 반영된다")
    void unmatchedSettlement_addsToUnmatchedIncomeOnly() {
        settlementMapper.seed(unmatched(TARGET_DATE, 10_000L, 2));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(0L, result.getTotalIncome());
        assertEquals(10_000L, result.getUnmatchedIncome());
        assertEquals(0, result.getMatchedTransactionCount());
        assertEquals(2, result.getUnmatchedTransactionCount());
        assertEquals(0, result.getAmbiguousTransactionCount());
        assertTrue(result.getJobIncomes().isEmpty());
        assertNull(result.getPrimaryJobId());
    }

    @Test
    @DisplayName("해당 월 SETTLEMENT가 전혀 없으면 총소득 0원, 잡별 소득은 빈 리스트, 주 소득원은 null이다")
    void noIncome_returnsEmptyJobIncomesAndNullPrimaryJob() {
        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(0L, result.getTotalIncome());
        assertEquals(0L, result.getUnmatchedIncome());
        assertTrue(result.getJobIncomes().isEmpty());
        assertNull(result.getPrimaryJobId());
        assertNull(result.getPrimaryJobName());
    }

    @Test
    @DisplayName("잡별 소득이 동률로 최고액이면 주 소득원을 임의로 정하지 않고 null을 반환한다")
    void tiedPrimaryJobIncome_returnsNullPrimaryJob() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        jobMapper.seed(Job.builder().jobId(JOB_B).userId(USER_ID).jobName("잡비").build());
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 10_000L, 1));
        settlementMapper.seed(matched(JOB_B, TARGET_DATE, 10_000L, 1));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(2, result.getJobIncomes().size());
        assertNull(result.getPrimaryJobId());
    }

    @Test
    @DisplayName("전월 SETTLEMENT가 실제로 없으면 소득변화액은 이번 달 소득과 같고 변화율은 null이다")
    void previousMonthActuallyZero_changeRateNull() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 30_000L, 1));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(0L, result.getPreviousMonthIncome());
        assertEquals(30_000L, result.getIncomeChangeAmount());
        assertNull(result.getIncomeChangeRate());
    }

    @Test
    @DisplayName("전월 소득이 있으면 소득변화액/변화율이 정상 계산된다")
    void previousMonthNonZero_changeRateCalculated() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        settlementMapper.seed(matched(JOB_A, PREVIOUS_MONTH_DATE, 20_000L, 1));
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 30_000L, 1));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(20_000L, result.getPreviousMonthIncome());
        assertEquals(10_000L, result.getIncomeChangeAmount());
        assertEquals(0.5, result.getIncomeChangeRate());
    }

    @Test
    @DisplayName("전월 SETTLEMENT 조회가 실패하면 실제 0원과 구분하기 위해 null로 반환한다")
    void previousMonthFetchFails_returnsNullNotZero() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 30_000L, 1));
        settlementMapper.failFor(YearMonth.of(2026, 6));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(30_000L, result.getTotalIncome());
        assertNull(result.getPreviousMonthIncome());
        assertNull(result.getIncomeChangeAmount());
        assertNull(result.getIncomeChangeRate());
    }

    @Test
    @DisplayName("이번 달 SETTLEMENT 조회 자체가 실패하면 빈 정상 응답 대신 예외를 전파한다")
    void currentMonthFetchFails_propagatesException() {
        settlementMapper.failFor(TARGET);

        assertThrows(RuntimeException.class, () -> service.getMonthlyIncomeAnalysis(USER_ID, TARGET));
    }

    @Test
    @DisplayName("유효한 월이 2개 미만이면 변동성은 null이다")
    void volatility_lessThanTwoValidMonths_returnsNull() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 30_000L, 1));
        settlementMapper.failFor(YearMonth.of(2026, 6));
        settlementMapper.failFor(YearMonth.of(2026, 5));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertNull(result.getIncomeVolatility());
    }

    @Test
    @DisplayName("최근 3개월 평균소득이 0이면 변동성은 null이다")
    void volatility_meanIncomeZero_returnsNull() {
        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertNull(result.getIncomeVolatility());
    }

    @Test
    @DisplayName("최근 3개월 소득이 모두 확인되면 변동계수가 계산된다")
    void volatility_threeValidMonths_calculatesCoefficient() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        settlementMapper.seed(matched(JOB_A, LocalDate.of(2026, 5, 10), 10_000L, 1));
        settlementMapper.seed(matched(JOB_A, PREVIOUS_MONTH_DATE, 20_000L, 1));
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 30_000L, 1));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        // mean=20000, population stddev = sqrt(((10000-20000)^2+0+(10000)^2)/3) ≈ 8164.97
        assertEquals(8164.97 / 20000.0, result.getIncomeVolatility(), 0.001);
    }

    @Test
    @DisplayName("발생소득(WorkLog 기준)과 실입금소득(SETTLEMENT 기준)을 잡별로 비교한다")
    void earnedDepositComparison_comparesWorkLogEarnedAndSettlementDeposited() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        settlementMapper.seed(matched(JOB_A, TARGET_DATE, 40_000L, 1));
        workLogMapper.insert(workLog(JOB_A, TARGET_DATE, "CONFIRMED", 50_000L, 3L, SettlementStatus.COMPLETED));
        workLogMapper.insert(workLog(JOB_A, TARGET_DATE.plusDays(1), "PLANNED", 99_999L, 3L, SettlementStatus.NONE));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(1, result.getEarnedDepositComparisons().size());
        EarnedDepositComparison comparison = result.getEarnedDepositComparisons().get(0);
        assertEquals(JOB_A, comparison.getJobId());
        assertEquals(50_000L, comparison.getEarnedIncome());
        assertEquals(40_000L, comparison.getDepositedIncome());
        assertEquals(-10_000L, comparison.getDifferenceAmount());
    }

    @Test
    @DisplayName("확정됐지만 아직 COMPLETED가 아닌 WORK_LOG_PLATFORM_INCOME 행의 소득만 pendingSettlementIncome에 합산된다")
    void pendingSettlementIncome_sumsConfirmedButNotYetCompletedIncomeRows() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());

        WorkLog pendingLog = workLog(JOB_A, TARGET_DATE, "CONFIRMED", 30_000L, 3L, SettlementStatus.PENDING);
        WorkLog completedLog = workLog(JOB_A, TARGET_DATE.plusDays(1), "CONFIRMED", 20_000L, 3L, SettlementStatus.COMPLETED);
        WorkLog plannedLog = workLog(JOB_A, TARGET_DATE.plusDays(2), "PLANNED", 99_999L, 3L, SettlementStatus.NONE);
        workLogMapper.insert(pendingLog);
        workLogMapper.insert(completedLog);
        workLogMapper.insert(plannedLog);
        insertIncome(pendingLog, 30_000L, SettlementStatus.PENDING);
        insertIncome(completedLog, 20_000L, SettlementStatus.COMPLETED);
        // plannedLog는 CONFIRMED가 아니라 income 행 자체를 안 만듦 (실제 WorkLogService 동작과 동일)

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(30_000L, result.getPendingSettlementIncome());
    }

    private void insertIncome(WorkLog workLog, long amount, SettlementStatus status) {
        workLogPlatformIncomeMapper.insert(com.ntropy.work.domain.entity.WorkLogPlatformIncome.builder()
                .logId(workLog.getLogId())
                .platformId(PLATFORM_ID)
                .expectedAmount(amount)
                .settlementStatus(status)
                .build());
    }

    @Test
    @DisplayName("잡별 피로도는 근무시간 가중평균이며 근무시간이 없으면 null이다")
    void fatigueSummary_weightedAverageByWorkMinutes() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("잡에이").build());
        workLogMapper.insert(workLogWithTime(JOB_A, TARGET_DATE, "CONFIRMED", 10_000L, 2L,
                LocalTime.of(9, 0), LocalTime.of(11, 0)));
        workLogMapper.insert(workLogWithTime(JOB_A, TARGET_DATE.plusDays(1), "PLANNED", 10_000L, 4L,
                LocalTime.of(9, 0), LocalTime.of(10, 0)));

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertEquals(1, result.getFatigueSummaries().size());
        JobFatigueSummary fatigue = result.getFatigueSummaries().get(0);
        assertEquals(2, fatigue.getWorkDays());
        assertEquals(180L, fatigue.getTotalWorkMinutes());
        // (2*120 + 4*60) / 180 = 480/180 ≈ 2.666...
        assertEquals(480.0 / 180.0, fatigue.getAverageFatigue(), 0.001);
        assertEquals(4L, fatigue.getLatestFatigue());
    }

    @Test
    @DisplayName("활동(매칭소득/근무일지)이 없는 잡은 발생소득 비교와 피로도 목록에 포함하지 않는다")
    void jobsWithoutActivity_areExcludedFromComparisonsAndFatigue() {
        jobMapper.seed(Job.builder().jobId(JOB_A).userId(USER_ID).jobName("활동없는잡").build());

        MonthlyIncomeAnalysisSummary result = service.getMonthlyIncomeAnalysis(USER_ID, TARGET);

        assertTrue(result.getEarnedDepositComparisons().isEmpty());
        assertTrue(result.getFatigueSummaries().isEmpty());
    }

    private static Settlement matched(Long jobId, LocalDate depositDate, long amount, int count) {
        return Settlement.builder()
                .userId(USER_ID)
                .status(SettlementMatchStatus.MATCHED)
                .jobId(jobId)
                .periodStart(depositDate)
                .periodEnd(depositDate)
                .depositDate(depositDate)
                .expectedAmount(0L)
                .actualAmount(amount)
                .transactionCount(count)
                .accountTransactionId(1L)
                .matchedAt(LocalDateTime.now())
                .build();
    }

    private static Settlement unmatched(LocalDate depositDate, long amount, int count) {
        return Settlement.builder()
                .userId(USER_ID)
                .status(SettlementMatchStatus.UNMATCHED)
                .jobId(null)
                .periodStart(depositDate)
                .periodEnd(depositDate)
                .depositDate(depositDate)
                .expectedAmount(0L)
                .actualAmount(amount)
                .transactionCount(count)
                .accountTransactionId(null)
                .matchedAt(LocalDateTime.now())
                .build();
    }

    private static WorkLog workLog(Long jobId, LocalDate workDate, String status, long estimatedIncome, long fatigue,
                                    SettlementStatus settlementStatus) {
        return workLogWithTime(jobId, workDate, status, estimatedIncome, fatigue,
                LocalTime.of(9, 0), LocalTime.of(10, 0), settlementStatus);
    }

    private static WorkLog workLogWithTime(Long jobId, LocalDate workDate, String status, long estimatedIncome,
                                            long fatigue, LocalTime startTime, LocalTime endTime) {
        return workLogWithTime(jobId, workDate, status, estimatedIncome, fatigue, startTime, endTime,
                SettlementStatus.NONE);
    }

    private static WorkLog workLogWithTime(Long jobId, LocalDate workDate, String status, long estimatedIncome,
                                            long fatigue, LocalTime startTime, LocalTime endTime,
                                            SettlementStatus settlementStatus) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(jobId)
                .workDate(workDate)
                .startTime(startTime)
                .endTime(endTime)
                .estimatedIncome(estimatedIncome)
                .fatigue(fatigue)
                .status(status)
                .settlementStatus(settlementStatus)
                .build();
    }

    private static final class StubSettlementMapper implements SettlementMapper {
        private final List<Settlement> all = new ArrayList<>();
        private final List<YearMonth> failingMonths = new ArrayList<>();

        void seed(Settlement settlement) {
            all.add(settlement);
        }

        void failFor(YearMonth yearMonth) {
            failingMonths.add(yearMonth);
        }

        @Override
        public void insert(Settlement settlement) {
            throw new UnsupportedOperationException("이 테스트 더블은 조회만 지원합니다.");
        }

        @Override
        public boolean existsByAccountTransactionId(Long accountTransactionId) {
            throw new UnsupportedOperationException("이 테스트 더블은 조회만 지원합니다.");
        }

        @Override
        public boolean existsByUserIdAndStatusAndPeriod(Long userId, SettlementMatchStatus status,
                                                         LocalDate periodStart, LocalDate periodEnd) {
            throw new UnsupportedOperationException("이 테스트 더블은 조회만 지원합니다.");
        }

        @Override
        public List<Settlement> findByUserIdAndDepositDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
            if (failingMonths.contains(YearMonth.from(startDate))) {
                throw new RuntimeException("조회 실패 (테스트용)");
            }
            return all.stream()
                    .filter(s -> s.getUserId().equals(userId))
                    .filter(s -> !s.getDepositDate().isBefore(startDate) && !s.getDepositDate().isAfter(endDate))
                    .toList();
        }

        @Override
        public List<Settlement> findByUserIdInAndDepositDateRange(List<Long> userIds, LocalDate startDate,
                                                                    LocalDate endDate) {
            if (failingMonths.contains(YearMonth.from(startDate))) {
                throw new RuntimeException("조회 실패 (테스트용)");
            }
            return all.stream()
                    .filter(s -> userIds.contains(s.getUserId()))
                    .filter(s -> !s.getDepositDate().isBefore(startDate) && !s.getDepositDate().isAfter(endDate))
                    .toList();
        }

        @Override
        public List<Settlement> findByJobIdInAndDepositDateRangeAndStatus(List<Long> jobIds, LocalDate startDate,
                                                                           LocalDate endDate,
                                                                           SettlementMatchStatus status) {
            throw new UnsupportedOperationException("이 테스트 더블은 조회만 지원합니다.");
        }
    }
}
