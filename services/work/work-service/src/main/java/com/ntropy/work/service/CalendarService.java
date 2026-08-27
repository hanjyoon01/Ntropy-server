package com.ntropy.work.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.work.api.client.WeatherQueryClient;
import com.ntropy.work.api.dto.summary.CalendarDailySummary;
import com.ntropy.work.api.dto.summary.CalendarDaySummary;
import com.ntropy.work.api.dto.summary.CalendarFatigueGauge;
import com.ntropy.work.api.dto.summary.CalendarJobBrief;
import com.ntropy.work.api.dto.summary.CalendarMonthlyHours;
import com.ntropy.work.api.dto.summary.CalendarMonthlySummary;
import com.ntropy.work.api.dto.summary.CalendarWorkBrief;
import com.ntropy.work.api.dto.summary.WeatherForecast;
import com.ntropy.work.api.dto.summary.WeatherForecastList;
import com.ntropy.work.domain.WorkLogStatus;
import com.ntropy.work.domain.entity.AllocationGoal;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.AllocationGoalMapper;
import com.ntropy.work.mapper.SavingGoalMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final String DAY_SETTLEMENT_COMPLETED = "COMPLETED";
    private static final String DAY_SETTLEMENT_PENDING = "PENDING";

    private static final Map<DayOfWeek, String> KOREAN_DAY_OF_WEEK = new EnumMap<>(DayOfWeek.class);
    static {
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.MONDAY, "월");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.TUESDAY, "화");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.WEDNESDAY, "수");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.THURSDAY, "목");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.FRIDAY, "금");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.SATURDAY, "토");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.SUNDAY, "일");
    }

    private final WorkLogMapper workLogMapper;
    private final WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;
    private final AllocationGoalMapper allocationGoalMapper;
    private final SavingGoalMapper savingGoalMapper;
    private final JobService jobService;
    private final FatigueService fatigueService;
    private final WeatherQueryClient weatherQueryClient;

    public CalendarMonthlySummary getMonthlySummary(Long userId, int year, int month, Double latitude, Double longitude) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        String targetMonth = yearMonth.toString(); // "2026-07"

        List<Job> jobs = jobService.findByUserId(userId);
        Map<Long, String> jobNames = jobs.stream()
                .collect(Collectors.toMap(Job::getJobId, Job::getJobName, (a, b) -> a));
        List<Long> jobIds = jobs.stream().map(Job::getJobId).collect(Collectors.toList());

        List<WorkLog> workLogs = workLogMapper.findByUserIdAndDateRange(userId, startDate, endDate);
        List<WorkLogPlatformIncome> confirmedIncomes =
                workLogPlatformIncomeMapper.findConfirmedByUserIdAndDateRange(userId, startDate, endDate);
        List<AllocationGoal> allocationGoals = jobIds.isEmpty()
                ? List.of()
                : allocationGoalMapper.findByJobIdsAndTargetMonth(jobIds, targetMonth);
        SavingGoal savingGoal = savingGoalMapper.findByUserIdAndTargetMonth(userId, targetMonth);

        Map<LocalDate, WeatherForecast> weatherByDate = weatherByDate(latitude, longitude);

        CalendarMonthlyHours hours = summarizeHours(workLogs, confirmedIncomes, allocationGoals, savingGoal);
        List<CalendarDaySummary> days = summarizeDays(workLogs, jobNames, weatherByDate);

        return new CalendarMonthlySummary(year, month, hours, days);
    }

    /**
     * fatigue는 7일 가중 게이지로 계산한다.
     */
    public CalendarDailySummary getDailySummary(Long userId, LocalDate date, Double latitude, Double longitude) {
        // 피로도 게이지(7일 가중)에 쓸 범위로 한 번에 조회하고, 당일 근무 목록은 이 결과에서 걸러 쓴다
        // (예전에는 당일 WorkLog를 여기서 한 번, FatigueService 내부에서 또 한 번 중복 조회했음).
        List<WorkLog> recentWorkLogs = workLogMapper.findByUserIdAndDateRange(
                userId, date.minusDays(FatigueService.WINDOW_DAYS - 1L), date);
        List<WorkLog> workLogs = recentWorkLogs.stream()
                .filter(w -> date.equals(w.getWorkDate()))
                .collect(Collectors.toList());

        List<Long> jobIds = workLogs.stream().map(WorkLog::getJobId).distinct().collect(Collectors.toList());
        Map<Long, String> jobNames = jobIds.stream()
                .collect(Collectors.toMap(jobId -> jobId, jobId -> jobService.findById(jobId).getJobName()));

        List<CalendarWorkBrief> works = workLogs.stream()
                .map(w -> new CalendarWorkBrief(w.getLogId(), w.getJobId(), jobNames.get(w.getJobId()),
                        w.getStartTime(), w.getEndTime(), w.getStatus(), w.getSettlementStatus().name(),
                        w.getTaskCount(), w.getFatigue()))
                .collect(Collectors.toList());

        String dayOfWeek = KOREAN_DAY_OF_WEEK.get(date.getDayOfWeek());
        CalendarFatigueGauge fatigue = fatigueService.calculateGauge(userId, date, recentWorkLogs);
        WeatherForecast weather = weatherByDate(latitude, longitude).get(date);

        return new CalendarDailySummary(date, dayOfWeek, works, fatigue, weather);
    }

    /**
     * 단기예보(3~5일치)를 날짜 기준 Map으로 변환. 범위 밖 날짜는 Map에 아예 없다(null 취급).
     * 날씨는 부가 정보이므로 조회 실패(예외/응답 누락) 시에도 캘린더 조회 자체는 실패하지 않도록 빈 Map을 반환한다.
     */
    private Map<LocalDate, WeatherForecast> weatherByDate(Double latitude, Double longitude) {
        try {
            WeatherForecastList forecastList = weatherQueryClient.getForecasts(latitude, longitude);
            if (forecastList == null || forecastList.getForecasts() == null) {
                return Map.of();
            }
            return forecastList.getForecasts().stream()
                    .collect(Collectors.toMap(WeatherForecast::getDate, f -> f, (a, b) -> a));
        } catch (Exception e) {
            log.warn("날씨 예보 조회 실패 - latitude={}, longitude={}", latitude, longitude, e);
            return Map.of();
        }
    }

    /**
     * goalHours: 해당 월 ALLOCATION_GOAL(잡별 추천 근무시간) 합
     * confirmedHours/scheduledHours: 해당 월 WORK_LOG를 status(CONFIRMED/PLANNED) 기준으로 나눈 근무시간 합
     * expectedSettlementIncome: expectedIncome(전체 예상소득) - CONFIRMED 근무일지에 딸린
     *   WORK_LOG_PLATFORM_INCOME 행 중 이미 COMPLETED로 정산 완료된 행들의 expectedAmount 합.
     *   플랫폼별 income 행 단위로 계산하므로, 여러 플랫폼 중 일부만 정산된 PARTIAL 근무일지도
     *   완료된 플랫폼 몫만 정확히 제외된다. 실제 입금액(actualIncome)은 SETTLEMENT 테이블
     *   기반이라 여기서는 계산하지 않는다 - IncomeAnalysisQueryClient를 따로 써야 한다.
     * targetAmount: 해당 월 SAVING_GOAL이 없으면 null (달성률 미표시는 프론트 책임)
     */
    private CalendarMonthlyHours summarizeHours(List<WorkLog> workLogs, List<WorkLogPlatformIncome> confirmedIncomes,
                                                 List<AllocationGoal> allocationGoals, SavingGoal savingGoal) {
        int goalHours = allocationGoals.stream()
                .mapToInt(goal -> goal.getRecommendHour() == null ? 0 : goal.getRecommendHour().intValue())
                .sum();

        int confirmedHours = 0;
        int scheduledHours = 0;
        long expectedIncome = 0;
        for (WorkLog workLog : workLogs) {
            int hours = WorkTimeUtils.durationHours(workLog.getStartTime(), workLog.getEndTime());
            long income = workLog.getEstimatedIncome() == null ? 0 : workLog.getEstimatedIncome();
            if (WorkLogStatus.CONFIRMED.equals(workLog.getStatus())) {
                confirmedHours += hours;
            } else if (WorkLogStatus.PLANNED.equals(workLog.getStatus())) {
                scheduledHours += hours;
            }
            expectedIncome += income;
        }

        long completedFromPlatforms = confirmedIncomes.stream()
                .filter(income -> SettlementStatus.COMPLETED.equals(income.getSettlementStatus()))
                .mapToLong(income -> income.getExpectedAmount() == null ? 0 : income.getExpectedAmount())
                .sum();
        long expectedSettlementIncome = expectedIncome - completedFromPlatforms;

        Long targetAmount = savingGoal == null ? null : savingGoal.getTargetAmount();
        return new CalendarMonthlyHours(goalHours, confirmedHours, scheduledHours, expectedIncome,
                expectedSettlementIncome, targetAmount);
    }

    private List<CalendarDaySummary> summarizeDays(List<WorkLog> workLogs, Map<Long, String> jobNames,
                                                     Map<LocalDate, WeatherForecast> weatherByDate) {
        Map<LocalDate, List<WorkLog>> byDate = new TreeMap<>();
        for (WorkLog workLog : workLogs) {
            byDate.computeIfAbsent(workLog.getWorkDate(), d -> new ArrayList<>()).add(workLog);
        }

        List<CalendarDaySummary> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<WorkLog>> entry : byDate.entrySet()) {
            List<WorkLog> dayLogs = entry.getValue();

            boolean allCompleted = dayLogs.stream()
                    .allMatch(w -> SettlementStatus.COMPLETED.equals(w.getSettlementStatus()));
            String settlementStatus = allCompleted ? DAY_SETTLEMENT_COMPLETED : DAY_SETTLEMENT_PENDING;

            Map<Long, CalendarJobBrief> jobsById = new LinkedHashMap<>();
            for (WorkLog workLog : dayLogs) {
                jobsById.computeIfAbsent(workLog.getJobId(),
                        jobId -> new CalendarJobBrief(jobId, jobNames.get(jobId)));
            }

            WeatherForecast weather = weatherByDate.get(entry.getKey());
            days.add(new CalendarDaySummary(entry.getKey(), settlementStatus, new ArrayList<>(jobsById.values()), weather));
        }
        return days;
    }
}
