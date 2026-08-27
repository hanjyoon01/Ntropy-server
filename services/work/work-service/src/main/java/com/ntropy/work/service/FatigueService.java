package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.work.api.dto.summary.CalendarFatigueGauge;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.mapper.SavingGoalMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;

/**
 * 7일 가중 피로도 게이지 (설계서 §3-2).
 *
 * T(적정 피로도)는 해당 월의 SAVING_GOAL.labor_intensity를 사용한다.
 * 아직 저축 목표를 등록하지 않은 달(온보딩 중 등)은 DEFAULT_TARGET_FATIGUE로 폴백한다.
 * H_ref(주간 N잡 기준시간)는 4주 데이터 기반 개인화 이슈 전까지 당분간 고정값으로 둔다.
 * 주업 제외 규칙은 JOB에 본업 여부 필드가 없어 이번 구현에서는 적용하지 않는다
 * (전체 잡을 F 계산에 포함).
 */
@Service
@RequiredArgsConstructor
public class FatigueService {

    /** 7일 가중 윈도우 크기. 호출부(CalendarService)가 조회 범위를 맞출 때 참조한다. */
    public static final int WINDOW_DAYS = 7;
    private static final double NORMALIZATION_HOURS = 168.0; // 7일 x 24시간
    private static final double BASELINE_EXPECTED_WEIGHT = 0.57; // 28/49

    private static final long DEFAULT_TARGET_FATIGUE = 3; // T, SAVING_GOAL 미등록 시 폴백값 (1~5)
    private static final double DEFAULT_WEEKLY_REFERENCE_HOURS = 15; // H_ref, 직장인+부업 병행 기준 임시값

    private static final int GAUGE_LOW_UPPER_BOUND = 70;
    private static final int GAUGE_MEDIUM_UPPER_BOUND = 100;
    private static final String LEVEL_LOW = "LOW";
    private static final String LEVEL_MEDIUM = "MEDIUM";
    private static final String LEVEL_HIGH = "HIGH";

    private final SavingGoalMapper savingGoalMapper;

    /**
     * workLogs는 호출부가 [date - (WINDOW_DAYS-1), date] 범위로 미리 조회해 넘겨줘야 한다.
     * 하루치씩 DB를 왕복하던 이전 구현(N+1)을 없애기 위해 벌크 조회 결과를 받아 인메모리에서 그룹핑한다.
     */
    public CalendarFatigueGauge calculateGauge(Long userId, LocalDate date, List<WorkLog> workLogs) {
        Map<LocalDate, List<WorkLog>> workLogsByDate = workLogs.stream()
                .collect(Collectors.groupingBy(WorkLog::getWorkDate));

        double weightedFatigue = 0;

        for (int n = 0; n < WINDOW_DAYS; n++) {
            LocalDate day = date.minusDays(n);
            double dayWeight = (WINDOW_DAYS - n) / (double) WINDOW_DAYS;

            for (WorkLog workLog : workLogsByDate.getOrDefault(day, List.of())) {
                int hours = WorkTimeUtils.durationHours(workLog.getStartTime(), workLog.getEndTime());
                weightedFatigue += hours * workLog.getFatigue() * dayWeight;
            }
        }

        long targetFatigue = resolveTargetFatigue(userId, date);
        double f = weightedFatigue / NORMALIZATION_HOURS;
        double b = targetFatigue * (DEFAULT_WEEKLY_REFERENCE_HOURS / NORMALIZATION_HOURS)
                * BASELINE_EXPECTED_WEIGHT;

        int score = b == 0 ? 0 : (int) Math.round(f / b * 100);
        String level = toLevel(score);
        boolean isOverThreshold = score > GAUGE_MEDIUM_UPPER_BOUND;

        return new CalendarFatigueGauge(score, level, isOverThreshold);
    }

    /**
     * 해당 월 SAVING_GOAL이 있으면 labor_intensity를 T로 쓰고, 없으면 기본값으로 폴백한다.
     */
    private long resolveTargetFatigue(Long userId, LocalDate date) {
        String targetMonth = YearMonth.from(date).toString();
        SavingGoal savingGoal = savingGoalMapper.findByUserIdAndTargetMonth(userId, targetMonth);
        return savingGoal != null ? savingGoal.getLaborIntensity() : DEFAULT_TARGET_FATIGUE;
    }

    private String toLevel(int score) {
        if (score < GAUGE_LOW_UPPER_BOUND) {
            return LEVEL_LOW;
        }
        if (score <= GAUGE_MEDIUM_UPPER_BOUND) {
            return LEVEL_MEDIUM;
        }
        return LEVEL_HIGH;
    }

}