package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.api.client.WeatherQueryClient;
import com.ntropy.work.api.dto.summary.CalendarDailySummary;
import com.ntropy.work.api.dto.summary.CalendarFatigueGauge;
import com.ntropy.work.api.dto.summary.CalendarMonthlySummary;
import com.ntropy.work.api.dto.summary.WeatherForecast;
import com.ntropy.work.api.dto.summary.WeatherForecastList;
import com.ntropy.work.domain.entity.AllocationGoal;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.InMemoryAllocationGoalMapper;
import com.ntropy.work.mapper.InMemoryCategoryMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobScheduleMapper;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;
import com.ntropy.work.mapper.InMemoryWorkLogPlatformIncomeMapper;

class CalendarServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_A = 10L;
    private static final Long JOB_B = 20L;
    private static final Long PLATFORM_A = 100L;
    private static final Long PLATFORM_B = 200L;
    private static final String TARGET_MONTH = "2026-08";

    private InMemoryWorkLogMapper workLogMapper;
    private InMemoryWorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;
    private InMemoryAllocationGoalMapper allocationGoalMapper;
    private InMemorySavingGoalMapper savingGoalMapper;
    private JobService jobService;
    private CalendarService calendarService;

    @BeforeEach
    void setUp() {
        InMemoryJobMapper jobMapper = new InMemoryJobMapper();
        jobMapper.seed(job(JOB_A, "배민 배달"));
        jobMapper.seed(job(JOB_B, "쿠팡이츠 배달"));

        workLogMapper = new InMemoryWorkLogMapper();
        workLogPlatformIncomeMapper = new InMemoryWorkLogPlatformIncomeMapper(workLogMapper);
        allocationGoalMapper = new InMemoryAllocationGoalMapper();
        savingGoalMapper = new InMemorySavingGoalMapper();
        SavingGoalService savingGoalService = new SavingGoalService(savingGoalMapper, allocationGoalMapper);
        RecommendedWorkHoursService recommendedWorkHoursService =
                new RecommendedWorkHoursService(savingGoalService, jobMapper, allocationGoalMapper);
        jobService = new JobService(
                jobMapper, new InMemoryJobScheduleMapper(), new CategoryService(new InMemoryCategoryMapper()),
                allocationGoalMapper, recommendedWorkHoursService
        );

        calendarService = new CalendarService(
                workLogMapper, workLogPlatformIncomeMapper, allocationGoalMapper, savingGoalMapper, jobService,
                new StubFatigueService(null), new StubWeatherQueryClient(List.of())
        );
    }

    private Job job(Long jobId, String jobName) {
        Job job = Job.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .jobName(jobName)
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(12000)
                .isRegular(false)
                .baseFatigue(3)
                .isActive(true)
                .build();
        job.setJobId(jobId);
        return job;
    }

    private WorkLog seedWorkLog(Long jobId, LocalDate date, LocalTime start, LocalTime end,
                                 String status, SettlementStatus settlementStatus, Long estimatedIncome) {
        WorkLog workLog = WorkLog.builder()
                .userId(USER_ID)
                .jobId(jobId)
                .workDate(date)
                .startTime(start)
                .endTime(end)
                .status(status)
                .settlementStatus(settlementStatus)
                .estimatedIncome(estimatedIncome)
                .build();
        workLogMapper.insert(workLog);
        return workLog;
    }

    private void seedIncome(Long logId, Long platformId, long expectedAmount, SettlementStatus settlementStatus) {
        workLogPlatformIncomeMapper.insert(WorkLogPlatformIncome.builder()
                .logId(logId)
                .platformId(platformId)
                .expectedAmount(expectedAmount)
                .settlementStatus(settlementStatus)
                .build());
    }


    @Test
    @DisplayName("월간 요약은 목표시간 합/예상수입 합/저축 목표 금액을 계산한다")
    void monthlySummary_aggregatesPlannedActualAndIncome() {
        allocationGoalMapper.insert(AllocationGoal.builder().jobId(JOB_A).targetMonth(TARGET_MONTH).recommendHour(20L).build());
        allocationGoalMapper.insert(AllocationGoal.builder().jobId(JOB_B).targetMonth(TARGET_MONTH).recommendHour(10L).build());
        savingGoalMapper.insert(SavingGoal.builder()
                .userId(USER_ID).targetMonth(TARGET_MONTH).targetAmount(2_500_000L).laborIntensity(3L).build());
        seedWorkLog(JOB_A, LocalDate.of(2026, 8, 3), LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);
        seedWorkLog(JOB_B, LocalDate.of(2026, 8, 5), LocalTime.of(10, 0), LocalTime.of(14, 0),
                "CONFIRMED", SettlementStatus.PENDING, 20000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertEquals(30, summary.getSummary().getGoalHours());
        assertEquals(68000L, summary.getSummary().getExpectedIncome());
        assertEquals(2_500_000L, summary.getSummary().getTargetAmount());
    }

    @Test
    @DisplayName("월간 요약의 근무시간은 status 기준으로 confirmedHours/scheduledHours에 나뉘어 집계된다")
    void monthlySummary_splitsHoursByStatus() {
        seedWorkLog(JOB_A, LocalDate.of(2026, 8, 3), LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);
        seedWorkLog(JOB_B, LocalDate.of(2026, 8, 5), LocalTime.of(10, 0), LocalTime.of(14, 0),
                "CONFIRMED", SettlementStatus.PENDING, 20000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertEquals(4, summary.getSummary().getConfirmedHours());
        assertEquals(4, summary.getSummary().getScheduledHours());
    }

    @Test
    @DisplayName("예상 정산 소득은 PLANNED 근무와, CONFIRMED 근무일지 중 아직 COMPLETED 안 된 플랫폼 몫을 합산한다")
    void monthlySummary_expectedSettlementIncome_includesPlannedAndConfirmedNotCompleted() {
        WorkLog completed = seedWorkLog(JOB_A, LocalDate.of(2026, 8, 3), LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);
        seedIncome(completed.getLogId(), PLATFORM_A, 36000L, SettlementStatus.COMPLETED);

        WorkLog pending = seedWorkLog(JOB_A, LocalDate.of(2026, 8, 5), LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.PENDING, 30000L);
        seedIncome(pending.getLogId(), PLATFORM_A, 30000L, SettlementStatus.PENDING);

        // 플랫폼 2개짜리 잡: 하나만 완료된 PARTIAL 근무. COMPLETED 몫(12000)은 정산 완료 처리되어
        // 나머지 PENDING 몫(8000)만 expectedSettlementIncome으로 들어가야 한다.
        WorkLog partial = seedWorkLog(JOB_A, LocalDate.of(2026, 8, 7), LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.PARTIAL, 20000L);
        seedIncome(partial.getLogId(), PLATFORM_A, 12000L, SettlementStatus.COMPLETED);
        seedIncome(partial.getLogId(), PLATFORM_B, 8000L, SettlementStatus.PENDING);

        seedWorkLog(JOB_B, LocalDate.of(2026, 8, 10), LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        // expectedSettlementIncome = 30000(pending) + 8000(partial의 미완료 몫) + 48000(planned) = 86000
        assertEquals(86000L, summary.getSummary().getExpectedSettlementIncome());
    }

    @Test
    @DisplayName("월 범위 밖의 근무일지는 집계에서 제외된다")
    void monthlySummary_excludesLogsOutsideDateRange() {
        seedWorkLog(JOB_A, LocalDate.of(2026, 7, 31), LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertEquals(0, summary.getSummary().getConfirmedHours());
        assertEquals(0, summary.getSummary().getScheduledHours());
        assertTrue(summary.getDays().isEmpty());
    }

    @Test
    @DisplayName("하루의 근무일지가 전부 COMPLETED여야 그날이 COMPLETED로 표시된다")
    void monthlySummary_dayIsCompletedOnlyWhenAllLogsCompleted() {
        LocalDate mixedDay = LocalDate.of(2026, 8, 3);
        seedWorkLog(JOB_A, mixedDay, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);
        seedWorkLog(JOB_B, mixedDay, LocalTime.of(14, 0), LocalTime.of(16, 0),
                "CONFIRMED", SettlementStatus.PENDING, 8000L);

        LocalDate completedDay = LocalDate.of(2026, 8, 10);
        seedWorkLog(JOB_A, completedDay, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertEquals("PENDING", findDay(summary, mixedDay).getSettlementStatus());
        assertEquals("COMPLETED", findDay(summary, completedDay).getSettlementStatus());
    }

    @Test
    @DisplayName("같은 날 같은 잡의 근무일지가 여러 개여도 잡 목록은 중복 없이 1개다")
    void monthlySummary_jobBriefsAreDeduplicatedPerDay() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        seedWorkLog(JOB_A, date, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);
        seedWorkLog(JOB_A, date, LocalTime.of(14, 0), LocalTime.of(16, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 24000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertEquals(1, findDay(summary, date).getJobs().size());
    }

    @Test
    @DisplayName("근무 이력이 없으면 시간 합계는 0이고 일자 목록은 비어 있다")
    void monthlySummary_noDataReturnsZeroedSummary() {
        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertEquals(0, summary.getSummary().getGoalHours());
        assertEquals(0, summary.getSummary().getConfirmedHours());
        assertEquals(0, summary.getSummary().getScheduledHours());
        assertEquals(0L, summary.getSummary().getExpectedIncome());
        assertTrue(summary.getDays().isEmpty());
    }

    @Test
    @DisplayName("해당 월 SAVING_GOAL이 없으면 targetAmount는 null이다")
    void monthlySummary_noSavingGoal_targetAmountIsNull() {
        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertNull(summary.getSummary().getTargetAmount());
    }

    @Test
    @DisplayName("다른 달의 SAVING_GOAL은 이번 달 targetAmount에 반영되지 않는다")
    void monthlySummary_savingGoalForOtherMonth_targetAmountIsNull() {
        savingGoalMapper.insert(SavingGoal.builder()
                .userId(USER_ID).targetMonth("2026-09").targetAmount(2_500_000L).laborIntensity(3L).build());

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertNull(summary.getSummary().getTargetAmount());
    }

    @Test
    @DisplayName("일간 요약은 근무 목록과 요일 한글 표기를 채운다")
    void dailySummary_returnsWorksAndKoreanDayOfWeek() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        WorkLog log = seedWorkLog(JOB_A, monday, LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);
        log.setFatigue(3L);
        workLogMapper.update(log);

        CalendarDailySummary summary = calendarService.getDailySummary(USER_ID, monday, null, null);

        assertEquals("월", summary.getDayOfWeek());
        assertEquals(1, summary.getWorks().size());
        assertEquals("배민 배달", summary.getWorks().get(0).getJobName());
        assertEquals(3L, summary.getWorks().get(0).getFatigue());
        assertEquals("NONE", summary.getWorks().get(0).getSettlementStatus());
    }

    @Test
    @DisplayName("일간 요약의 피로도 게이지는 FatigueService 결과를 그대로 담는다")
    void dailySummary_attachesFatigueGaugeFromFatigueService() {
        CalendarFatigueGauge gauge = new CalendarFatigueGauge(42, "LOW", false);
        InMemoryJobMapper freshJobMapper = new InMemoryJobMapper();
        InMemoryAllocationGoalMapper freshAllocationGoalMapper = new InMemoryAllocationGoalMapper();
        SavingGoalService freshSavingGoalService =
                new SavingGoalService(new InMemorySavingGoalMapper(), freshAllocationGoalMapper);
        RecommendedWorkHoursService freshRecommendedWorkHoursService =
                new RecommendedWorkHoursService(freshSavingGoalService, freshJobMapper, freshAllocationGoalMapper);
        JobService jobService = new JobService(
                freshJobMapper, new InMemoryJobScheduleMapper(), new CategoryService(new InMemoryCategoryMapper()),
                freshAllocationGoalMapper, freshRecommendedWorkHoursService
        );
        CalendarService service = new CalendarService(
                workLogMapper, workLogPlatformIncomeMapper, allocationGoalMapper, savingGoalMapper, jobService,
                new StubFatigueService(gauge), new StubWeatherQueryClient(List.of())
        );

        CalendarDailySummary summary = service.getDailySummary(USER_ID, LocalDate.of(2026, 8, 3), null, null);

        assertEquals(gauge, summary.getFatigue());
    }

    @Test
    @DisplayName("예보 범위 안의 날짜에는 날씨가 채워지고 범위 밖 날짜는 null이다")
    void monthlySummary_attachesWeatherOnlyForForecastDates() {
        LocalDate forecastDate = LocalDate.of(2026, 8, 3);
        LocalDate noForecastDate = LocalDate.of(2026, 8, 10);
        WeatherForecast forecast = new WeatherForecast(forecastDate, "맑음", "없음", false, 28);
        CalendarService service = new CalendarService(
                workLogMapper, workLogPlatformIncomeMapper, allocationGoalMapper, savingGoalMapper, jobService,
                new StubFatigueService(null), new StubWeatherQueryClient(List.of(forecast))
        );
        seedWorkLog(JOB_A, forecastDate, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);
        seedWorkLog(JOB_A, noForecastDate, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);

        CalendarMonthlySummary summary = service.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertEquals(forecast, findDay(summary, forecastDate).getWeather());
        assertNull(findDay(summary, noForecastDate).getWeather());
    }

    @Test
    @DisplayName("날씨 조회가 예외를 던져도 캘린더 집계 결과는 정상 반환되고 weather는 null이다")
    void monthlySummary_weatherQueryFails_degradesToNullWeather() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        CalendarService service = new CalendarService(
                workLogMapper, workLogPlatformIncomeMapper, allocationGoalMapper, savingGoalMapper, jobService,
                new StubFatigueService(null), new ThrowingWeatherQueryClient()
        );
        seedWorkLog(JOB_A, date, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);

        CalendarMonthlySummary summary = service.getMonthlySummary(USER_ID, 2026, 8, null, null);

        assertNull(findDay(summary, date).getWeather());
    }

    @Test
    @DisplayName("예보 목록이 null이어도 예외 없이 동작하고 weather는 null이다")
    void dailySummary_forecastListIsNull_returnsNullWeather() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        CalendarService service = new CalendarService(
                workLogMapper, workLogPlatformIncomeMapper, allocationGoalMapper, savingGoalMapper, jobService,
                new StubFatigueService(null), new StubWeatherQueryClient(null)
        );
        seedWorkLog(JOB_A, date, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);

        CalendarDailySummary summary = service.getDailySummary(USER_ID, date, null, null);

        assertNull(summary.getWeather());
    }

    private com.ntropy.work.api.dto.summary.CalendarDaySummary findDay(CalendarMonthlySummary summary, LocalDate date) {
        return summary.getDays().stream()
                .filter(day -> day.getDate().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("해당 날짜의 요약이 없습니다: " + date));
    }

    private static class StubFatigueService extends FatigueService {

        private final CalendarFatigueGauge gauge;

        StubFatigueService(CalendarFatigueGauge gauge) {
            super(null);
            this.gauge = gauge;
        }

        @Override
        public CalendarFatigueGauge calculateGauge(Long userId, LocalDate date, List<WorkLog> workLogs) {
            return gauge;
        }
    }

    private static class StubWeatherQueryClient implements WeatherQueryClient {

        private final List<WeatherForecast> forecasts;

        StubWeatherQueryClient(List<WeatherForecast> forecasts) {
            this.forecasts = forecasts;
        }

        @Override
        public WeatherForecastList getForecasts(Double latitude, Double longitude) {
            return new WeatherForecastList(forecasts);
        }
    }

    private static class ThrowingWeatherQueryClient implements WeatherQueryClient {

        @Override
        public WeatherForecastList getForecasts(Double latitude, Double longitude) {
            throw new RuntimeException("기상청 API 장애");
        }
    }
}
