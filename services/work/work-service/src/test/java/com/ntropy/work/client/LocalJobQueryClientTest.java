package com.ntropy.work.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.api.dto.summary.JobScheduleSummary;
import com.ntropy.work.api.dto.summary.JobSummary;
import com.ntropy.work.api.dto.summary.PlatformBrief;
import com.ntropy.work.domain.entity.Category;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobSchedule;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.InMemoryCategoryMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobPlatformMappingMapper;
import com.ntropy.work.mapper.InMemoryJobScheduleMapper;
import com.ntropy.work.mapper.InMemoryPlatformMapper;
import com.ntropy.work.service.CategoryService;
import com.ntropy.work.service.JobPlatformMappingService;
import com.ntropy.work.service.JobService;
import com.ntropy.work.service.PlatformService;
import com.ntropy.work.service.RecommendedWorkHoursService;
import com.ntropy.work.service.SavingGoalService;
import com.ntropy.work.mapper.InMemoryAllocationGoalMapper;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;

class LocalJobQueryClientTest {

    private static final Long USER_ID = 1L;

    private InMemoryJobMapper jobMapper;
    private InMemoryJobScheduleMapper jobScheduleMapper;
    private JobService jobService;
    private JobPlatformMappingService jobPlatformMappingService;
    private LocalJobQueryClient jobQueryClient;

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

        InMemoryPlatformMapper platformMapper = new InMemoryPlatformMapper();
        platformMapper.seed(Platform.builder().platformId(1L).categoryId(1L)
                .platformName("배달의민족").settlementCycle("DAILY").build());
        jobPlatformMappingService = new JobPlatformMappingService(new InMemoryJobPlatformMappingMapper(), platformMapper);

        jobQueryClient = new LocalJobQueryClient(jobService, jobPlatformMappingService, new PlatformService(platformMapper));
    }

    private Job.JobBuilder baseJob() {
        return Job.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("배민 배달")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(12000)
                .baseFatigue(3);
    }

    @Test
    @DisplayName("정기잡을 단건 조회하면 정기근무 스케줄이 함께 내려온다")
    void getJob_regularJob_includesSchedules() {
        Job job = jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(JobSchedule.builder().dayOfWeek("MON")
                        .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(22, 0)).build()));

        JobSummary summary = jobQueryClient.getJob(job.getJobId());

        assertEquals(1, summary.getSchedules().size());
        JobScheduleSummary schedule = summary.getSchedules().get(0);
        assertEquals("MON", schedule.getDayOfWeek());
        assertEquals(LocalTime.of(18, 0), schedule.getStartTime());
        assertEquals(LocalTime.of(22, 0), schedule.getEndTime());
    }

    @Test
    @DisplayName("비정기잡을 단건 조회하면 스케줄은 빈 리스트다")
    void getJob_nonRegularJob_hasEmptySchedules() {
        Job job = jobService.registerJob(baseJob().isRegular(false).build(), null);

        JobSummary summary = jobQueryClient.getJob(job.getJobId());

        assertTrue(summary.getSchedules().isEmpty());
    }

    @Test
    @DisplayName("유저의 잡 목록을 조회하면 각 잡에 맞는 스케줄이 채워진다")
    void getJobsByUserId_includesSchedulesPerJob() {
        Job regularJob = jobService.registerJob(baseJob().isRegular(true).build(),
                List.of(JobSchedule.builder().dayOfWeek("MON")
                        .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(22, 0)).build()));
        Job nonRegularJob = jobService.registerJob(
                baseJob().jobName("쿠팡이츠 배달").isRegular(false).build(), null);

        List<JobSummary> summaries = jobQueryClient.getJobsByUserId(USER_ID);

        JobSummary regularSummary = summaries.stream()
                .filter(s -> s.getJobId().equals(regularJob.getJobId()))
                .findFirst().orElseThrow();
        JobSummary nonRegularSummary = summaries.stream()
                .filter(s -> s.getJobId().equals(nonRegularJob.getJobId()))
                .findFirst().orElseThrow();

        assertEquals(1, regularSummary.getSchedules().size());
        assertTrue(nonRegularSummary.getSchedules().isEmpty());
    }

    @Test
    @DisplayName("잡에 연결된 플랫폼이 있으면 단건 조회 시 함께 내려온다")
    void getJob_withPlatformMapping_includesPlatforms() {
        Job job = jobService.registerJob(baseJob().isRegular(false).build(), null);
        jobPlatformMappingService.register(job.getJobId(), 1L);

        JobSummary summary = jobQueryClient.getJob(job.getJobId());

        assertEquals(1, summary.getPlatforms().size());
        PlatformBrief platform = summary.getPlatforms().get(0);
        assertEquals(1L, platform.getPlatformId());
        assertEquals("배달의민족", platform.getPlatformName());
    }

    @Test
    @DisplayName("연결된 플랫폼이 없으면 platforms는 빈 리스트다")
    void getJob_withoutPlatformMapping_hasEmptyPlatforms() {
        Job job = jobService.registerJob(baseJob().isRegular(false).build(), null);

        JobSummary summary = jobQueryClient.getJob(job.getJobId());

        assertTrue(summary.getPlatforms().isEmpty());
    }
}
