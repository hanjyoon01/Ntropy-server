package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.api.dto.summary.CalendarFatigueGauge;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;

class FatigueServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 10);

    private InMemoryWorkLogMapper workLogMapper;
    private InMemorySavingGoalMapper savingGoalMapper;
    private FatigueService fatigueService;

    @BeforeEach
    void setUp() {
        workLogMapper = new InMemoryWorkLogMapper();
        savingGoalMapper = new InMemorySavingGoalMapper();
        fatigueService = new FatigueService(savingGoalMapper);
    }

    private void seedWorkLog(Long jobId, LocalDate date, LocalTime start, LocalTime end, long fatigue) {
        workLogMapper.insert(WorkLog.builder()
                .userId(USER_ID)
                .jobId(jobId)
                .workDate(date)
                .startTime(start)
                .endTime(end)
                .status("CONFIRMED")
                .settlementStatus(SettlementStatus.PENDING)
                .fatigue(fatigue)
                .build());
    }

    /** 실제 호출부(CalendarService)와 동일하게, 7일 윈도우 범위를 벌크 조회해서 넘겨준다. */
    private CalendarFatigueGauge calculateGauge(LocalDate date) {
        List<WorkLog> workLogs = workLogMapper.findByUserIdAndDateRange(
                USER_ID, date.minusDays(FatigueService.WINDOW_DAYS - 1L), date);
        return fatigueService.calculateGauge(USER_ID, date, workLogs);
    }

    @Test
    @DisplayName("7일간 근무 기록이 없으면 게이지는 0점, LOW다")
    void calculateGauge_noWorkLogs_returnsZero() {
        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(0, gauge.getScore());
        assertEquals("LOW", gauge.getLevel());
        assertFalse(gauge.getIsOverThreshold());
    }

    @Test
    @DisplayName("당일 8시간 근무(fatigue 3)는 MEDIUM 구간 점수로 계산된다")
    void calculateGauge_todayEightHours_isMediumLevel() {
        seedWorkLog(1L, TARGET_DATE, LocalTime.of(9, 0), LocalTime.of(17, 0), 3);

        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(94, gauge.getScore());
        assertEquals("MEDIUM", gauge.getLevel());
        assertFalse(gauge.getIsOverThreshold());
    }

    @Test
    @DisplayName("6일 전 근무는 가중치가 낮아져 LOW 구간 점수로 계산된다")
    void calculateGauge_sixDaysAgoSameHours_isLowerScoreThanToday() {
        seedWorkLog(1L, TARGET_DATE.minusDays(6), LocalTime.of(9, 0), LocalTime.of(17, 0), 3);

        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(13, gauge.getScore());
        assertEquals("LOW", gauge.getLevel());
    }

    @Test
    @DisplayName("고강도 근무는 HIGH 구간과 임계값 초과로 계산된다")
    void calculateGauge_highIntensityWork_isOverThreshold() {
        seedWorkLog(1L, TARGET_DATE, LocalTime.of(6, 0), LocalTime.of(16, 0), 5);

        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(195, gauge.getScore());
        assertEquals("HIGH", gauge.getLevel());
        assertTrue(gauge.getIsOverThreshold());
    }

    @Test
    @DisplayName("7일 윈도우보다 이전의 근무 기록은 집계에서 제외된다")
    void calculateGauge_workLogOutsideWindow_isExcluded() {
        seedWorkLog(1L, TARGET_DATE.minusDays(7), LocalTime.of(9, 0), LocalTime.of(17, 0), 3);

        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(0, gauge.getScore());
    }

    @Test
    @DisplayName("같은 날 여러 잡의 근무는 각 fatigue가 합산된다")
    void calculateGauge_multipleJobsSameDay_sumsContributions() {
        seedWorkLog(1L, TARGET_DATE, LocalTime.of(9, 0), LocalTime.of(13, 0), 3);
        seedWorkLog(2L, TARGET_DATE, LocalTime.of(14, 0), LocalTime.of(18, 0), 3);

        CalendarFatigueGauge combined = calculateGauge(TARGET_DATE);

        assertEquals(94, combined.getScore());
    }

    @Test
    @DisplayName("계획 단계라도 WorkLog.fatigue 값이 그대로 게이지 계산에 쓰인다")
    void calculateGauge_usesWorkLogFatigueDirectly_notJobBaseFatigue() {
        seedWorkLog(1L, TARGET_DATE, LocalTime.of(9, 0), LocalTime.of(17, 0), 5);

        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(156, gauge.getScore());
    }

    @Test
    @DisplayName("해당 월 SAVING_GOAL이 있으면 labor_intensity가 T로 쓰여 게이지 점수가 달라진다")
    void calculateGauge_savingGoalPresent_usesLaborIntensityAsTargetFatigue() {
        seedWorkLog(1L, TARGET_DATE, LocalTime.of(9, 0), LocalTime.of(17, 0), 3);
        savingGoalMapper.insert(SavingGoal.builder()
                .userId(USER_ID).targetMonth("2026-08").targetAmount(2_500_000L).laborIntensity(5L).build());

        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(56, gauge.getScore());
        assertEquals("LOW", gauge.getLevel());
    }

    @Test
    @DisplayName("다른 달의 SAVING_GOAL은 이번 달 계산에 영향을 주지 않고 기본값으로 폴백한다")
    void calculateGauge_savingGoalForOtherMonth_fallsBackToDefault() {
        seedWorkLog(1L, TARGET_DATE, LocalTime.of(9, 0), LocalTime.of(17, 0), 3);
        savingGoalMapper.insert(SavingGoal.builder()
                .userId(USER_ID).targetMonth("2026-09").targetAmount(2_500_000L).laborIntensity(5L).build());

        CalendarFatigueGauge gauge = calculateGauge(TARGET_DATE);

        assertEquals(94, gauge.getScore());
        assertEquals("MEDIUM", gauge.getLevel());
    }
}
