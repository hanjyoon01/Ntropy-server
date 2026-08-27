package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;

import com.ntropy.work.mapper.InMemoryAllocationGoalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.entity.Category;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobSchedule;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.InMemoryCategoryMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobScheduleMapper;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;

class JobServiceTest {

    private static final Long USER_ID = 1L;

    private InMemoryJobMapper jobMapper;
    private InMemoryJobScheduleMapper jobScheduleMapper;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobMapper = new InMemoryJobMapper();
        jobScheduleMapper = new InMemoryJobScheduleMapper();
        InMemoryCategoryMapper categoryMapper = new InMemoryCategoryMapper();
        categoryMapper.seed(Category.builder().categoryId(1L).name("배달").build());
        InMemoryAllocationGoalMapper allocationGoalMapper = new InMemoryAllocationGoalMapper();
        SavingGoalService savingGoalService = new SavingGoalService(new InMemorySavingGoalMapper(), allocationGoalMapper);
        RecommendedWorkHoursService recommendedWorkHoursService =
                new RecommendedWorkHoursService(savingGoalService, jobMapper, allocationGoalMapper);
        jobService = new JobService(jobMapper, jobScheduleMapper, new CategoryService(categoryMapper),
                allocationGoalMapper, recommendedWorkHoursService);
    }

    private Job.JobBuilder baseJob() {
        return Job.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("배민 배달")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(12000)
                .isRegular(false)
                .baseFatigue(3);
    }

    private JobSchedule scheduleOf(String dayOfWeek, LocalTime start, LocalTime end) {
        return JobSchedule.builder().dayOfWeek(dayOfWeek).startTime(start).endTime(end).build();
    }

    @Test
    @DisplayName("스케줄 없이 잡을 등록할 수 있다")
    void registerJob_withoutSchedule_succeeds() {
        Job job = jobService.registerJob(baseJob().build(), null);

        assertTrue(job.getIsActive());
        assertEquals(1, jobMapper.findByUserId(USER_ID).size());
    }

    @Test
    @DisplayName("HOURLY 잡은 hourlyWage가 없으면 등록에 실패한다")
    void registerJob_hourlyMissingWage_throws() {
        Job job = baseJob().hourlyWage(null).build();

        assertThrows(ServiceException.class, () -> jobService.registerJob(job, null));
    }

    @Test
    @DisplayName("PER_TASK 잡은 taskPerHour가 없으면 등록에 실패한다")
    void registerJob_perTaskMissingTaskPerHour_throws() {
        Job job = baseJob()
                .settlementType(SettlementType.PER_TASK)
                .hourlyWage(null)
                .perTaskWage(3000)
                .build();

        assertThrows(ServiceException.class, () -> jobService.registerJob(job, null));
    }

    @Test
    @DisplayName("MONTHLY 잡은 monthlyWage가 없으면 등록에 실패한다")
    void registerJob_monthlyMissingWage_throws() {
        Job job = baseJob().settlementType(SettlementType.MONTHLY).hourlyWage(null).build();

        assertThrows(ServiceException.class, () -> jobService.registerJob(job, null));
    }

    @Test
    @DisplayName("정기잡은 스케줄 없이 등록하면 실패한다")
    void registerJob_regularWithoutSchedule_throws() {
        Job job = baseJob().isRegular(true).build();

        assertThrows(ServiceException.class, () -> jobService.registerJob(job, null));
    }

    @Test
    @DisplayName("비정기잡에 스케줄을 넣으면 등록에 실패한다")
    void registerJob_nonRegularWithSchedule_throws() {
        Job job = baseJob().isRegular(false).build();
        List<JobSchedule> schedules = List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0)));

        assertThrows(ServiceException.class, () -> jobService.registerJob(job, schedules));
    }

    @Test
    @DisplayName("정기잡을 스케줄과 함께 등록하면 둘 다 저장된다")
    void registerJob_regularWithSchedule_registersBoth() {
        Job job = baseJob().isRegular(true).build();
        List<JobSchedule> schedules = List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0)));

        Job result = jobService.registerJob(job, schedules);

        assertEquals(1, jobScheduleMapper.findByJobId(result.getJobId()).size());
    }

    @Test
    @DisplayName("신규 스케줄끼리 시간이 겹치면 등록에 실패한다")
    void registerJob_newSchedulesOverlapWithEachOther_throws() {
        Job job = baseJob().isRegular(true).build();
        List<JobSchedule> schedules = List.of(
                scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0)),
                scheduleOf("MON", LocalTime.of(21, 0), LocalTime.of(23, 0))
        );

        assertThrows(ServiceException.class, () -> jobService.registerJob(job, schedules));
    }

    @Test
    @DisplayName("같은 유저의 다른 잡 스케줄과 겹치면 등록에 실패한다")
    void registerJob_overlapsWithAnotherJobOfSameUser_throws() {
        jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0))));

        Job secondJob = baseJob().jobName("쿠팡이츠 배달").isRegular(true).build();
        List<JobSchedule> overlapping = List.of(scheduleOf("MON", LocalTime.of(21, 0), LocalTime.of(23, 0)));

        assertThrows(ServiceException.class, () -> jobService.registerJob(secondJob, overlapping));
    }

    @Test
    @DisplayName("같은 시간이라도 요일이 다르면 등록할 수 있다")
    void registerJob_sameTimeDifferentDay_succeeds() {
        jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0))));

        Job secondJob = baseJob().jobName("쿠팡이츠 배달").isRegular(true).build();
        List<JobSchedule> differentDay = List.of(scheduleOf("TUE", LocalTime.of(18, 0), LocalTime.of(22, 0)));

        Job result = jobService.registerJob(secondJob, differentDay);

        assertEquals(1, jobScheduleMapper.findByJobId(result.getJobId()).size());
    }

    @Test
    @DisplayName("스케줄 겹침 검증은 유저 단위로만 적용된다")
    void registerJob_overlapCheckIsScopedPerUser() {
        jobMapper.seed(baseJob().jobId(999L).userId(2L).build());
        jobScheduleMapper.insert(
                JobSchedule.builder().jobId(999L).dayOfWeek("MON")
                        .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(22, 0)).build()
        );

        Job job = baseJob().isRegular(true).build();
        List<JobSchedule> schedules = List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0)));

        Job result = jobService.registerJob(job, schedules);

        assertEquals(1, jobScheduleMapper.findByJobId(result.getJobId()).size());
    }

    @Test
    @DisplayName("수정 시에도 정산 필드 검증이 다시 적용된다")
    void updateJob_reappliesSettlementValidation() {
        Job job = jobService.registerJob(baseJob().build(), null);
        Job patch = baseJob().jobId(job.getJobId()).hourlyWage(null).build();

        assertThrows(ServiceException.class, () -> jobService.updateJob(USER_ID, patch, null));
    }

    @Test
    @DisplayName("존재하지 않는 잡을 수정하면 실패한다")
    void updateJob_notFound_throws() {
        Job patch = baseJob().jobId(999L).build();

        assertThrows(ServiceException.class, () -> jobService.updateJob(USER_ID, patch, null));
    }

    @Test
    @DisplayName("다른 유저의 잡을 수정하면 실패한다")
    void updateJob_notOwner_throws() {
        Job job = jobService.registerJob(baseJob().build(), null);
        Job patch = baseJob().jobId(job.getJobId()).build();

        assertThrows(ServiceException.class, () -> jobService.updateJob(999L, patch, null));
    }

    @Test
    @DisplayName("비활성화하면 isActive가 false가 된다")
    void deactivateJob_setsIsActiveFalse() {
        Job job = jobService.registerJob(baseJob().build(), null);

        jobService.deactivateJob(USER_ID, job.getJobId());

        assertEquals(false, jobService.findById(job.getJobId()).getIsActive());
    }

    @Test
    @DisplayName("다른 유저의 잡을 비활성화하면 실패한다")
    void deactivateJob_notOwner_throws() {
        Job job = jobService.registerJob(baseJob().build(), null);

        assertThrows(ServiceException.class, () -> jobService.deactivateJob(999L, job.getJobId()));
    }

    @Test
    @DisplayName("존재하지 않는 잡을 조회하면 실패한다")
    void findById_notFound_throws() {
        assertThrows(ServiceException.class, () -> jobService.findById(999L));
    }

    @Test
    @DisplayName("MONTHLY 잡의 월 예상 소득은 월급 그대로다")
    void registerJob_monthly_usesMonthlyWageAsExpectedIncome() {
        Job job = baseJob()
                .settlementType(SettlementType.MONTHLY)
                .hourlyWage(null)
                .monthlyWage(2500000)
                .build();

        Job result = jobService.registerJob(job, null);

        assertEquals(2500000L, result.getMonthlyExpectedIncome());
    }

    @Test
    @DisplayName("HOURLY 잡의 월 예상 소득은 주간 근무시간을 30일로 환산해 계산한다")
    void registerJob_hourly_calculatesFromWeeklyScheduleHours() {
        // 주 15시간(월/수/금 각 5시간) x 30/7주 환산 x 시급 12,000원
        Job job = baseJob().isRegular(true).hourlyWage(12000).build();
        List<JobSchedule> schedules = List.of(
                scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(23, 0)),
                scheduleOf("WED", LocalTime.of(18, 0), LocalTime.of(23, 0)),
                scheduleOf("FRI", LocalTime.of(18, 0), LocalTime.of(23, 0))
        );

        Job result = jobService.registerJob(job, schedules);

        assertEquals(Math.round(15 * (30.0 / 7.0) * 12000), result.getMonthlyExpectedIncome());
    }

    @Test
    @DisplayName("스케줄 없는 HOURLY 잡은 월 예상 소득을 계산할 수 없다")
    void registerJob_hourlyWithoutSchedule_hasNullExpectedIncome() {
        Job result = jobService.registerJob(baseJob().build(), null);

        assertNull(result.getMonthlyExpectedIncome());
    }

    @Test
    @DisplayName("PER_TASK 잡은 계산 방식이 없어 월 예상 소득이 null이다")
    void registerJob_perTask_hasNullExpectedIncome() {
        Job job = baseJob()
                .settlementType(SettlementType.PER_TASK)
                .hourlyWage(null)
                .perTaskWage(3000)
                .taskPerHour(4.0f)
                .build();

        Job result = jobService.registerJob(job, null);

        assertNull(result.getMonthlyExpectedIncome());
    }

    @Test
    @DisplayName("시급을 수정하면 월 예상 소득도 다시 계산된다")
    void updateJob_recalculatesMonthlyExpectedIncome() {
        Job job = jobService.registerJob(baseJob().isRegular(true).hourlyWage(12000).build(),
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(23, 0))));

        Job patch = baseJob().jobId(job.getJobId()).isRegular(true).hourlyWage(15000).build();
        Job result = jobService.updateJob(USER_ID, patch,
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(23, 0))));

        assertEquals(Math.round(5 * (30.0 / 7.0) * 15000), result.getMonthlyExpectedIncome());
    }

    @Test
    @DisplayName("정기잡을 스케줄 없이 수정하면 실패한다")
    void updateJob_regularWithoutSchedule_throws() {
        Job job = jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0))));
        Job patch = baseJob().jobId(job.getJobId()).isRegular(true).build();

        assertThrows(ServiceException.class, () -> jobService.updateJob(USER_ID, patch, null));
    }

    @Test
    @DisplayName("잡을 수정하면 기존 스케줄이 새 스케줄로 통째로 교체된다")
    void updateJob_replacesSchedulesEntirely() {
        Job job = jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0))));

        Job patch = baseJob().jobId(job.getJobId()).isRegular(true).build();
        List<JobSchedule> newSchedules = List.of(scheduleOf("TUE", LocalTime.of(9, 0), LocalTime.of(13, 0)));
        jobService.updateJob(USER_ID, patch, newSchedules);

        List<JobSchedule> saved = jobScheduleMapper.findByJobId(job.getJobId());
        assertEquals(1, saved.size());
        assertEquals("TUE", saved.get(0).getDayOfWeek());
    }

    @Test
    @DisplayName("수정 시 자기 자신의 기존 스케줄과 같은 시간대를 다시 넣어도 겹침으로 실패하지 않는다")
    void updateJob_sameScheduleAsBefore_doesNotThrowOverlap() {
        Job job = jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0))));

        Job patch = baseJob().jobId(job.getJobId()).isRegular(true).build();
        List<JobSchedule> sameSchedule = List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0)));

        Job result = jobService.updateJob(USER_ID, patch, sameSchedule);

        assertEquals(1, jobScheduleMapper.findByJobId(result.getJobId()).size());
    }

    @Test
    @DisplayName("수정 시 다른 잡의 스케줄과 겹치면 실패한다")
    void updateJob_overlapsWithAnotherJob_throws() {
        jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(scheduleOf("MON", LocalTime.of(18, 0), LocalTime.of(22, 0))));
        Job secondJob = jobService.registerJob(baseJob().jobName("쿠팡이츠 배달").isRegular(true).build(),
                List.of(scheduleOf("TUE", LocalTime.of(18, 0), LocalTime.of(22, 0))));

        Job patch = baseJob().jobId(secondJob.getJobId()).jobName("쿠팡이츠 배달").isRegular(true).build();
        List<JobSchedule> overlapping = List.of(scheduleOf("MON", LocalTime.of(19, 0), LocalTime.of(21, 0)));

        assertThrows(ServiceException.class, () -> jobService.updateJob(USER_ID, patch, overlapping));
    }
}
