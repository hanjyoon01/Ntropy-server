package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.InMemoryAllocationGoalMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;

class RecommendedWorkHoursServiceTest {

    private static final Long USER_ID = 1L;

    private InMemoryJobMapper jobMapper;
    private InMemorySavingGoalMapper savingGoalMapper;
    private InMemoryAllocationGoalMapper allocationGoalMapper;

    private RecommendedWorkHoursService service;

    @BeforeEach
    void setUp() {
        jobMapper = new InMemoryJobMapper();
        savingGoalMapper = new InMemorySavingGoalMapper();
        allocationGoalMapper = new InMemoryAllocationGoalMapper();

        SavingGoalService savingGoalService =
                new SavingGoalService(
                        savingGoalMapper,
                        allocationGoalMapper
                );

        service = new RecommendedWorkHoursService(
                savingGoalService,
                jobMapper,
                allocationGoalMapper
        );
    }

    private void registerCurrentMonthGoal(
            long targetAmount,
            long laborIntensity
    ) {
        /*
         * SavingGoalMapper.insert()는 반환값이 없으므로
         * insert()만 호출하면 됩니다.
         */
        savingGoalMapper.insert(
                SavingGoal.builder()
                        .userId(USER_ID)
                        .targetMonth(YearMonth.now().toString())
                        .targetAmount(targetAmount)
                        .laborIntensity(laborIntensity)
                        .build()
        );
    }

    @Test
    @DisplayName("월급형 잡은 추천 대상에서 제외한다")
    void monthlyJobExcluded() {
        registerCurrentMonthGoal(2_200_000L, 3L);

        Job monthlyJob = Job.builder()
                .jobId(1L)
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("월급형 잡")
                .settlementType(SettlementType.MONTHLY)
                .monthlyWage(1_200_000)
                .isRegular(true)
                .baseFatigue(2)
                .isActive(true)
                .build();

        Job hourlyJob = Job.builder()
                .jobId(2L)
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("시급형 잡")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(30_000)
                .isRegular(false)
                .baseFatigue(2)
                .isActive(true)
                .build();

        jobMapper.seed(monthlyJob);
        jobMapper.seed(hourlyJob);

        /*
         * 인메모리 매퍼가 실제 DB 조인을 대신할 수 있도록
         * 잡과 사용자의 관계를 등록합니다.
         */
        allocationGoalMapper.registerJobOwner(1L, USER_ID);
        allocationGoalMapper.registerJobOwner(2L, USER_ID);

        var result =
                service.getCurrentMonthRecommendedWorkHours(USER_ID);

        assertTrue(
                result.getRecommendedJobs()
                        .stream()
                        .noneMatch(job ->
                                job.getJobId().equals(1L))
        );

        assertTrue(
                result.getRecommendedJobs()
                        .stream()
                        .anyMatch(job ->
                                job.getJobId().equals(2L))
        );
    }

    @Test
    @DisplayName("비활성 잡은 추천 대상에서 제외한다")
    void inactiveJobExcluded() {
        registerCurrentMonthGoal(500_000L, 3L);

        Job inactiveJob = Job.builder()
                .jobId(1L)
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("비활성 잡")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(50_000)
                .isRegular(false)
                .baseFatigue(1)
                .isActive(false)
                .build();

        Job activeJob = Job.builder()
                .jobId(2L)
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("활성 잡")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(20_000)
                .isRegular(false)
                .baseFatigue(2)
                .isActive(true)
                .build();

        jobMapper.seed(inactiveJob);
        jobMapper.seed(activeJob);

        allocationGoalMapper.registerJobOwner(1L, USER_ID);
        allocationGoalMapper.registerJobOwner(2L, USER_ID);

        var result =
                service.getCurrentMonthRecommendedWorkHours(USER_ID);

        assertTrue(
                result.getRecommendedJobs()
                        .stream()
                        .noneMatch(job ->
                                job.getJobId().equals(1L))
        );

        assertTrue(
                result.getRecommendedJobs()
                        .stream()
                        .allMatch(job ->
                                job.getJobId().equals(2L))
        );
    }

    @Test
    @DisplayName("추천 결과를 ALLOCATION_GOAL에 저장한다")
    void resultStoredInAllocationGoal() {
        registerCurrentMonthGoal(600_000L, 3L);

        Job hourlyJob = Job.builder()
                .jobId(1L)
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("시급형 잡")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(20_000)
                .isRegular(false)
                .baseFatigue(1)
                .isActive(true)
                .build();

        jobMapper.seed(hourlyJob);
        allocationGoalMapper.registerJobOwner(1L, USER_ID);

        service.getCurrentMonthRecommendedWorkHours(USER_ID);

        var saved =
                allocationGoalMapper.findByUserIdAndTargetMonth(
                        USER_ID,
                        YearMonth.now().toString()
                );

        assertEquals(1, saved.size());
        assertEquals(30L, saved.get(0).getRecommendHour());
    }

    @Test
    @DisplayName("두 번째 조회에서는 기존 저장 결과를 재사용한다")
    void savedResultReused() {
        registerCurrentMonthGoal(600_000L, 3L);

        Job hourlyJob = Job.builder()
                .jobId(1L)
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("시급형 잡")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(20_000)
                .isRegular(false)
                .baseFatigue(1)
                .isActive(true)
                .build();

        jobMapper.seed(hourlyJob);
        allocationGoalMapper.registerJobOwner(1L, USER_ID);

        var first =
                service.getCurrentMonthRecommendedWorkHours(USER_ID);

        var second =
                service.getCurrentMonthRecommendedWorkHours(USER_ID);

        assertEquals(
                first.getTotalRecommendedHours(),
                second.getTotalRecommendedHours()
        );

        assertEquals(
                first.getRecommendedJobs().size(),
                second.getRecommendedJobs().size()
        );
    }
}