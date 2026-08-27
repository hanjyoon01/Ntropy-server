package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemorySettlementMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;
import com.ntropy.work.mapper.InMemoryWorkLogPlatformIncomeMapper;

/**
 * IncomeAnalysisService.getMonthlyIncomeAnalysisBulk 전용 테스트.
 * 단건 경로(getMonthlyIncomeAnalysis)는 IncomeAnalysisServiceTest에서 이미 검증하므로,
 * 여기서는 (1) 벌크 결과가 단건 결과와 동일한지, (2) 여러 사용자가 서로 섞이지 않는지에 집중한다.
 */
class IncomeAnalysisServiceBulkTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long JOB_A = 100L;
    private static final Long JOB_B = 200L;
    private static final Long PLATFORM_ID = 900L;
    private static final YearMonth TARGET = YearMonth.of(2026, 7);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDate PREVIOUS_MONTH_DATE = LocalDate.of(2026, 6, 10);

    private InMemorySettlementMapper settlementMapper;
    private InMemoryJobMapper jobMapper;
    private InMemoryWorkLogMapper workLogMapper;
    private InMemoryWorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;
    private IncomeAnalysisService service;

    @BeforeEach
    void setUp() {
        settlementMapper = new InMemorySettlementMapper();
        jobMapper = new InMemoryJobMapper();
        workLogMapper = new InMemoryWorkLogMapper();
        workLogPlatformIncomeMapper = new InMemoryWorkLogPlatformIncomeMapper(workLogMapper);
        service = new IncomeAnalysisService(settlementMapper, jobMapper, workLogMapper, workLogPlatformIncomeMapper);
    }

    @Test
    @DisplayName("userIds가 비어있으면 빈 Map을 반환한다")
    void emptyUserIds_returnsEmptyMap() {
        assertTrue(service.getMonthlyIncomeAnalysisBulk(List.of(), TARGET).isEmpty());
    }

    @Test
    @DisplayName("벌크 결과는 userIds에 있는 사용자 전원에 대해 하나씩 존재한다")
    void bulkResult_containsEntryForEveryRequestedUser() {
        jobMapper.seed(job(JOB_A, USER_A, "잡에이"));
        settlementMapper.insert(matched(USER_A, JOB_A, TARGET_DATE, 50_000L, 1));

        Map<Long, MonthlyIncomeAnalysisSummary> result =
                service.getMonthlyIncomeAnalysisBulk(List.of(USER_A, USER_B), TARGET);

        assertEquals(2, result.size());
        assertTrue(result.containsKey(USER_A));
        assertTrue(result.containsKey(USER_B));
    }

    @Test
    @DisplayName("데이터가 전혀 없는 사용자는 0/빈 값으로 채워진 요약을 받는다 (null이 아니다)")
    void userWithNoData_getsZeroedSummaryNotNull() {
        MonthlyIncomeAnalysisSummary summary =
                service.getMonthlyIncomeAnalysisBulk(List.of(USER_B), TARGET).get(USER_B);

        assertNotNull(summary);
        assertEquals(0L, summary.getTotalIncome());
        // 벌크 경로는 사용자 단위 조회 격리가 없어 조회 실패와 실제 0원을 구분하지 않는다 -
        // 단건 경로(§8)와 달리 previousMonthIncome이 null이 아니라 0L로 내려온다.
        assertEquals(0L, summary.getPreviousMonthIncome());
        assertTrue(summary.getJobIncomes().isEmpty());
        assertTrue(summary.getFatigueSummaries().isEmpty());
    }

    @Test
    @DisplayName("두 사용자의 SETTLEMENT/WORK_LOG가 서로 섞이지 않고 각자에게만 집계된다")
    void twoUsers_dataDoesNotCrossOver() {
        jobMapper.seed(job(JOB_A, USER_A, "A의 잡"));
        jobMapper.seed(job(JOB_B, USER_B, "B의 잡"));
        settlementMapper.insert(matched(USER_A, JOB_A, TARGET_DATE, 50_000L, 1));
        settlementMapper.insert(matched(USER_B, JOB_B, TARGET_DATE, 70_000L, 1));

        Map<Long, MonthlyIncomeAnalysisSummary> result =
                service.getMonthlyIncomeAnalysisBulk(List.of(USER_A, USER_B), TARGET);

        assertEquals(50_000L, result.get(USER_A).getTotalIncome());
        assertEquals(1, result.get(USER_A).getJobIncomes().size());
        assertEquals(JOB_A, result.get(USER_A).getJobIncomes().get(0).getJobId());

        assertEquals(70_000L, result.get(USER_B).getTotalIncome());
        assertEquals(1, result.get(USER_B).getJobIncomes().size());
        assertEquals(JOB_B, result.get(USER_B).getJobIncomes().get(0).getJobId());
    }

    @Test
    @DisplayName("WorkLogPlatformIncome은 userId가 없어 WorkLog(logId)로 매핑되는데, 이 매핑도 사용자별로 섞이지 않는다")
    void pendingSettlementIncome_doesNotCrossOverBetweenUsers() {
        jobMapper.seed(job(JOB_A, USER_A, "A의 잡"));
        jobMapper.seed(job(JOB_B, USER_B, "B의 잡"));

        WorkLog logA = workLog(USER_A, JOB_A, TARGET_DATE, SettlementStatus.PENDING);
        WorkLog logB = workLog(USER_B, JOB_B, TARGET_DATE, SettlementStatus.PENDING);
        workLogMapper.insert(logA);
        workLogMapper.insert(logB);
        insertIncome(logA, 30_000L, SettlementStatus.PENDING);
        insertIncome(logB, 45_000L, SettlementStatus.PENDING);

        Map<Long, MonthlyIncomeAnalysisSummary> result =
                service.getMonthlyIncomeAnalysisBulk(List.of(USER_A, USER_B), TARGET);

        assertEquals(30_000L, result.get(USER_A).getPendingSettlementIncome());
        assertEquals(45_000L, result.get(USER_B).getPendingSettlementIncome());
    }

    @Test
    @DisplayName("벌크 결과는 단건 조회(getMonthlyIncomeAnalysis)와 동일한 값을 낸다")
    void bulkResult_matchesSingleUserResult() {
        // previousMonthIncome이 실제로 존재하는 시나리오로 구성한다 - 데이터가 없을 때는
        // 단건(null=조회실패)과 벌크(0L=격리 없음)의 null 의미가 달라 직접 비교가 안 맞기 때문.
        jobMapper.seed(job(JOB_A, USER_A, "A의 잡"));
        settlementMapper.insert(matched(USER_A, JOB_A, PREVIOUS_MONTH_DATE, 20_000L, 1));
        settlementMapper.insert(matched(USER_A, JOB_A, TARGET_DATE, 30_000L, 1));
        WorkLog confirmedLog = workLog(USER_A, JOB_A, TARGET_DATE, SettlementStatus.COMPLETED);
        confirmedLog.setStatus("CONFIRMED");
        confirmedLog.setEstimatedIncome(30_000L);
        workLogMapper.insert(confirmedLog);

        MonthlyIncomeAnalysisSummary single = service.getMonthlyIncomeAnalysis(USER_A, TARGET);
        MonthlyIncomeAnalysisSummary bulk =
                service.getMonthlyIncomeAnalysisBulk(List.of(USER_A), TARGET).get(USER_A);

        assertEquals(single.getTotalIncome(), bulk.getTotalIncome());
        assertEquals(single.getUnmatchedIncome(), bulk.getUnmatchedIncome());
        assertEquals(single.getPreviousMonthIncome(), bulk.getPreviousMonthIncome());
        assertEquals(single.getIncomeChangeAmount(), bulk.getIncomeChangeAmount());
        assertEquals(single.getIncomeChangeRate(), bulk.getIncomeChangeRate());
        assertEquals(single.getJobIncomes().size(), bulk.getJobIncomes().size());
        assertEquals(single.getPrimaryJobId(), bulk.getPrimaryJobId());
        assertEquals(single.getEarnedDepositComparisons().size(), bulk.getEarnedDepositComparisons().size());
        assertEquals(single.getFatigueSummaries().size(), bulk.getFatigueSummaries().size());
    }

    private void insertIncome(WorkLog workLog, long amount, SettlementStatus status) {
        workLogPlatformIncomeMapper.insert(WorkLogPlatformIncome.builder()
                .logId(workLog.getLogId())
                .platformId(PLATFORM_ID)
                .expectedAmount(amount)
                .settlementStatus(status)
                .build());
    }

    private static Job job(Long jobId, Long userId, String name) {
        return Job.builder().jobId(jobId).userId(userId).jobName(name).build();
    }

    private static Settlement matched(Long userId, Long jobId, LocalDate depositDate, long amount, int count) {
        return Settlement.builder()
                .userId(userId)
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

    private static WorkLog workLog(Long userId, Long jobId, LocalDate workDate, SettlementStatus settlementStatus) {
        return WorkLog.builder()
                .userId(userId)
                .jobId(jobId)
                .workDate(workDate)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .estimatedIncome(0L)
                .fatigue(3L)
                .status("CONFIRMED")
                .settlementStatus(settlementStatus)
                .build();
    }
}
