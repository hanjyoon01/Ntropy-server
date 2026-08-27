package com.ntropy.work.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.mapper.InMemoryAllocationGoalMapper;
import com.ntropy.work.mapper.InMemorySettlementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.api.dto.summary.JobExpectedIncomeLossSummary;
import com.ntropy.work.domain.entity.Category;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.InMemoryCategoryMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobScheduleMapper;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;
import com.ntropy.work.service.CategoryService;
import com.ntropy.work.service.JobService;
import com.ntropy.work.service.RecommendedWorkHoursService;
import com.ntropy.work.service.SavingGoalService;

class LocalExpectedIncomeLossQueryClientTest {

    private static final Long USER_ID = 1L;

    private InMemoryJobMapper jobMapper;
    private InMemorySettlementMapper settlementMapper;
    private LocalExpectedIncomeLossQueryClient client;

    @BeforeEach
    void setUp() {
        jobMapper = new InMemoryJobMapper();
        settlementMapper = new InMemorySettlementMapper();
        InMemoryCategoryMapper categoryMapper = new InMemoryCategoryMapper();
        categoryMapper.seed(Category.builder().categoryId(1L).name("배달").build());
        InMemoryAllocationGoalMapper allocationGoalMapper = new InMemoryAllocationGoalMapper();
        SavingGoalService savingGoalService = new SavingGoalService(new InMemorySavingGoalMapper(), allocationGoalMapper);
        RecommendedWorkHoursService recommendedWorkHoursService =
                new RecommendedWorkHoursService(savingGoalService, jobMapper, allocationGoalMapper);
        JobService jobService = new JobService(
                jobMapper, new InMemoryJobScheduleMapper(), new CategoryService(categoryMapper),
                allocationGoalMapper, recommendedWorkHoursService);
        client = new LocalExpectedIncomeLossQueryClient(jobService, settlementMapper);
    }

    private Job.JobBuilder baseJob() {
        return Job.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("배민 배달")
                .settlementType(SettlementType.MONTHLY)
                .monthlyWage(3000000)
                .isRegular(false)
                .baseFatigue(3)
                .isActive(true);
    }

    private Settlement.SettlementBuilder matchedSettlement(Long jobId, LocalDate depositDate, long actualAmount) {
        return Settlement.builder()
                .userId(USER_ID)
                .jobId(jobId)
                .status(SettlementMatchStatus.MATCHED)
                .depositDate(depositDate)
                .actualAmount(actualAmount)
                .transactionCount(1);
    }

    @Test
    @DisplayName("방어기간 30일이면 월 예상 소득 전액이 손실로 계산된다")
    void findExpectedIncomeLossByJob_fullMonth_returnsFullIncome() {
        jobMapper.seed(baseJob().jobId(1L).monthlyExpectedIncome(3000000L).build());

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));

        assertEquals(1, result.size());
        assertEquals(3000000L, result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("방어기간 15일이면 월 예상 소득의 절반이 손실로 계산된다")
    void findExpectedIncomeLossByJob_halfMonth_returnsHalfIncome() {
        jobMapper.seed(baseJob().jobId(1L).monthlyExpectedIncome(3000000L).build());

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15));

        assertEquals(1500000L, result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("월 경계를 넘는 기간도 전체 일수 기준으로 계산된다")
    void findExpectedIncomeLossByJob_acrossMonths_usesTotalDays() {
        jobMapper.seed(baseJob().jobId(1L).monthlyExpectedIncome(3000000L).build());

        // 1/20~2/10 = 22일
        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 10));

        assertEquals(Math.round(3000000 * (22.0 / 30.0)), result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("월 예상 소득이 없는 잡은 손실액이 null로 반환된다")
    void findExpectedIncomeLossByJob_nullExpectedIncome_returnsNullLoss() {
        jobMapper.seed(baseJob().jobId(1L).monthlyExpectedIncome(null).build());

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));

        assertEquals(1, result.size());
        assertNull(result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("비활성 잡은 손실 계산에서 제외된다")
    void findExpectedIncomeLossByJob_inactiveJob_isExcluded() {
        jobMapper.seed(baseJob().jobId(1L).monthlyExpectedIncome(3000000L).isActive(false).build());

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("잡이 여러 개면 각각 손실액을 반환한다")
    void findExpectedIncomeLossByJob_multipleJobs_returnsEach() {
        jobMapper.seed(baseJob().jobId(1L).jobName("본업").monthlyExpectedIncome(3000000L).build());
        jobMapper.seed(baseJob().jobId(2L).jobName("배달").monthlyExpectedIncome(600000L).build());

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));

        assertEquals(2, result.size());
        assertEquals(3000000L, result.get(0).getExpectedIncomeLoss());
        assertEquals(600000L, result.get(1).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("등록된 잡이 없으면 빈 리스트를 반환한다")
    void findExpectedIncomeLossByJob_noJobs_returnsEmpty() {
        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("건당정산 잡은 최근 3개월(이번 달 제외) MATCHED 정산 평균으로 손실을 계산한다")
    void findExpectedIncomeLossByJob_perTask_usesRecentThreeMonthAverage() {
        jobMapper.seed(baseJob().jobId(1L).jobName("쿠팡플렉스")
                .settlementType(SettlementType.PER_TASK).monthlyWage(null).perTaskWage(3000).taskPerHour(3.5f)
                .monthlyExpectedIncome(null).build());

        YearMonth thisMonth = YearMonth.now();
        settlementMapper.insert(matchedSettlement(1L, thisMonth.minusMonths(1).atDay(10), 500000L).build());
        settlementMapper.insert(matchedSettlement(1L, thisMonth.minusMonths(2).atDay(10), 700000L).build());
        settlementMapper.insert(matchedSettlement(1L, thisMonth.minusMonths(3).atDay(10), 600000L).build());
        // 이번 달 정산은 아직 진행 중이라 평균 계산 대상에서 제외되어야 한다
        settlementMapper.insert(matchedSettlement(1L, thisMonth.atDay(1), 999999L).build());

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(29); // 30일

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(USER_ID, from, to);

        // 평균 = (500000+700000+600000)/3 = 600000, 30일 방어기간이면 전액 손실
        assertEquals(1, result.size());
        assertEquals(600000L, result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("건당정산 잡이 최근 3개월 중 일부만 정산 이력이 있으면 있는 달만으로 평균낸다")
    void findExpectedIncomeLossByJob_perTask_partialHistory_averagesOverAvailableMonthsOnly() {
        jobMapper.seed(baseJob().jobId(1L).jobName("쿠팡플렉스")
                .settlementType(SettlementType.PER_TASK).monthlyWage(null).perTaskWage(3000).taskPerHour(3.5f)
                .monthlyExpectedIncome(null).build());

        YearMonth thisMonth = YearMonth.now();
        // 최근 3개월 중 지난달 하나만 이력 존재 (신규 잡 등)
        settlementMapper.insert(matchedSettlement(1L, thisMonth.minusMonths(1).atDay(10), 450000L).build());

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(29);

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(USER_ID, from, to);

        assertEquals(450000L, result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("건당정산 잡이 최근 3개월 정산 이력이 전혀 없으면 손실액이 null로 반환된다")
    void findExpectedIncomeLossByJob_perTask_noHistory_returnsNullLoss() {
        jobMapper.seed(baseJob().jobId(1L).jobName("쿠팡플렉스")
                .settlementType(SettlementType.PER_TASK).monthlyWage(null).perTaskWage(3000).taskPerHour(3.5f)
                .monthlyExpectedIncome(null).build());

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(
                USER_ID, LocalDate.now(), LocalDate.now().plusDays(29));

        assertEquals(1, result.size());
        assertNull(result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("건당정산 잡과 다른 정산방식 잡이 섞여 있어도 각자의 방식으로 계산된다")
    void findExpectedIncomeLossByJob_mixedSettlementTypes_eachUsesOwnCalculation() {
        jobMapper.seed(baseJob().jobId(1L).jobName("본업").monthlyExpectedIncome(3000000L).build());
        jobMapper.seed(baseJob().jobId(2L).jobName("쿠팡플렉스")
                .settlementType(SettlementType.PER_TASK).monthlyWage(null).perTaskWage(3000).taskPerHour(3.5f)
                .monthlyExpectedIncome(null).build());

        YearMonth thisMonth = YearMonth.now();
        settlementMapper.insert(matchedSettlement(2L, thisMonth.minusMonths(1).atDay(10), 500000L).build());

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(29);

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(USER_ID, from, to);

        assertEquals(2, result.size());
        assertEquals(3000000L, result.get(0).getExpectedIncomeLoss());
        assertEquals(500000L, result.get(1).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("비정기(스케줄 없는) 시급 잡도 monthly_expected_income이 null이라 최근 3개월 평균으로 계산된다")
    void findExpectedIncomeLossByJob_irregularHourly_usesRecentThreeMonthAverage() {
        jobMapper.seed(baseJob().jobId(1L).jobName("단기 물류센터")
                .settlementType(SettlementType.HOURLY).monthlyWage(null).hourlyWage(12000)
                .monthlyExpectedIncome(null).build());

        YearMonth thisMonth = YearMonth.now();
        settlementMapper.insert(matchedSettlement(1L, thisMonth.minusMonths(1).atDay(10), 400000L).build());
        settlementMapper.insert(matchedSettlement(1L, thisMonth.minusMonths(2).atDay(10), 800000L).build());

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(29);

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(USER_ID, from, to);

        // 평균 = (400000+800000)/2 = 600000
        assertEquals(1, result.size());
        assertEquals(600000L, result.get(0).getExpectedIncomeLoss());
    }

    @Test
    @DisplayName("monthly_expected_income이 이미 있는 잡은 정산 이력이 있어도 스냅샷 값을 그대로 쓴다")
    void findExpectedIncomeLossByJob_hasSnapshot_ignoresSettlementHistory() {
        jobMapper.seed(baseJob().jobId(1L).jobName("본업").monthlyExpectedIncome(3000000L).build());

        YearMonth thisMonth = YearMonth.now();
        // 스냅샷이 있는 잡인데 정산 이력이 있어도 평균 계산에 쓰이면 안 된다
        settlementMapper.insert(matchedSettlement(1L, thisMonth.minusMonths(1).atDay(10), 100L).build());

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(29);

        List<JobExpectedIncomeLossSummary> result = client.findExpectedIncomeLossByJob(USER_ID, from, to);

        assertEquals(3000000L, result.get(0).getExpectedIncomeLoss());
    }
}
