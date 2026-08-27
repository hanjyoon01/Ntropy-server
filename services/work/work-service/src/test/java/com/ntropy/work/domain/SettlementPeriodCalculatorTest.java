package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.Platform;

class SettlementPeriodCalculatorTest {

    @Test
    @DisplayName("DAILY 정산은 offset일 이전 하루를 근무 기간으로 본다")
    void calculate_daily_subtractsOffsetDays() {
        Platform platform = Platform.builder().settlementCycle("DAILY").settlementOffsetDay(1).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 9), period.start());
        assertEquals(LocalDate.of(2026, 8, 9), period.end());
    }

    @Test
    @DisplayName("DAILY 정산에서 offset이 없으면 입금일 당일을 근무일로 본다")
    void calculate_daily_nullOffsetDefaultsToZero() {
        Platform platform = Platform.builder().settlementCycle("DAILY").settlementOffsetDay(null).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 10), period.start());
        assertEquals(LocalDate.of(2026, 8, 10), period.end());
    }

    @Test
    @DisplayName("WEEKLY 정산에서 offset이 없으면 입금일 하루 전까지 7일을 근무 기간으로 본다 (미검증 기본값)")
    void calculate_weekly_nullOffsetDefaultsToOneDay() {
        Platform platform = Platform.builder().settlementCycle("WEEKLY").settlementOffsetDay(null).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14), Set.of());

        assertEquals(LocalDate.of(2026, 8, 7), period.start());
        assertEquals(LocalDate.of(2026, 8, 13), period.end());
    }

    @Test
    @DisplayName("WEEKLY 정산은 offset일 이전까지 7일을 근무 기간으로 본다 (쿠팡이츠 배달파트너 실제 사례: 금요일 입금, offset=3 → 전주 수~이번주 화)")
    void calculate_weekly_withOffset_matchesCoupangEatsPattern() {
        Platform platform = Platform.builder().settlementCycle("WEEKLY").settlementOffsetDay(3).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14), Set.of());

        assertEquals(LocalDate.of(2026, 8, 5), period.start());
        assertEquals(LocalDate.of(2026, 8, 11), period.end());
    }

    @Test
    @DisplayName("MONTHLY 정산은 입금일이 속한 달의 직전 달 전체를 근무 기간으로 본다")
    void calculate_monthly_returnsPreviousCalendarMonth() {
        Platform platform = Platform.builder().settlementCycle("MONTHLY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 21), Set.of());

        assertEquals(LocalDate.of(2026, 7, 1), period.start());
        assertEquals(LocalDate.of(2026, 7, 31), period.end());
    }

    @Test
    @DisplayName("알 수 없는 정산 주기는 예외를 던진다")
    void calculate_unknownCycle_throws() {
        Platform platform = Platform.builder().settlementCycle("YEARLY").build();

        assertThrows(IllegalArgumentException.class,
                () -> SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 21), Set.of()));
    }

    @Test
    @DisplayName("CALENDAR_DAY(기본값)는 주말이 껴 있어도 그대로 달력일만큼 뺀다")
    void calculate_calendarDayUnit_ignoresWeekend() {
        // 2026-08-10(월)에서 3일 전 = 2026-08-07(금) - 주말(08/08,09) 안 건너뜀
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(3).settlementOffsetUnit("CALENDAR_DAY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 7), period.start());
    }

    @Test
    @DisplayName("BUSINESS_DAY는 역산 도중 낀 주말을 건너뛴다 (배민커넥트 사례: 월요일 입금, 3영업일 전)")
    void calculate_businessDayUnit_skipsWeekend() {
        // 2026-08-10(월)에서 영업일 3일 전: 금(08/07)-1, 목(08/06)-2, 수(08/05)-3
        // 중간에 낀 주말(08/08 토, 08/09 일)은 카운트하지 않음
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 5), period.start());
    }

    @Test
    @DisplayName("BUSINESS_DAY는 holidays로 주입된 공휴일도 건너뛴다")
    void calculate_businessDayUnit_skipsInjectedHoliday() {
        // 2026-08-17(월)에서 영업일 1일 전인 08/14(금)을 공휴일로 주입하면 08/13(목)이 됨
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(1).settlementOffsetUnit("BUSINESS_DAY").build();
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 8, 14));

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 17), holidays);

        assertEquals(LocalDate.of(2026, 8, 13), period.start());
    }

    @Test
    @DisplayName("DAILY+BUSINESS_DAY: 금·토·일 연속 근무는 전부 같은 입금일로 수렴하므로 기간이 셋을 다 포함해야 한다")
    void calculate_dailyBusinessDay_periodCoversWeekendRun() {
        // 금(08/14)에 일하고 3영업일 후 입금 = 월/화/수(08/17~19) 지나 수요일(08/19) 입금
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 19), Set.of());

        // workDate(금, 08/14)부터 다음 영업일(월, 08/17) 직전인 일요일(08/16)까지 - 금/토/일 전부 포함
        assertEquals(LocalDate.of(2026, 8, 14), period.start());
        assertEquals(LocalDate.of(2026, 8, 16), period.end());
    }

    @Test
    @DisplayName("DAILY+BUSINESS_DAY: 공휴일이 껴도 기간이 그 공휴일까지 포함해야 한다 (8/17 대체공휴일 실사례)")
    void calculate_dailyBusinessDay_periodCoversHolidayRun() {
        // 목(08/20) 입금, 3영업일 전 역산: 수(08/19)-1, 화(08/18)-2, 월(08/17,공휴일)은 건너뛰고 금(08/14)-3
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY").build();
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 8, 17));

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 20), holidays);

        // workDate(금, 08/14)부터 다음 영업일(화, 08/18) 직전인 08/17(월, 공휴일)까지 - 금/토/일/월(공휴일) 다 포함
        assertEquals(LocalDate.of(2026, 8, 14), period.start());
        assertEquals(LocalDate.of(2026, 8, 17), period.end());
    }

    @Test
    @DisplayName("WEEKLY + BUSINESS_DAY도 offset 역산 시 주말을 건너뛴다 (쿠팡이츠 배달파트너 실제 offset=3)")
    void calculate_weeklyBusinessDay_skipsWeekendForPeriodEnd() {
        Platform platform = Platform.builder()
                .settlementCycle("WEEKLY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY").build();

        // 2026-08-10(월)에서 영업일 3일 전 = 2026-08-05(수)
        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 5), period.end());
        assertEquals(LocalDate.of(2026, 7, 30), period.start());
    }

    @Test
    @DisplayName("WEEKLY + BUSINESS_DAY: periodEnd 경계일 자체가 공휴일이어도 그 경계일이 그대로 기간에 포함돼야 한다 (쿠팡이츠 화요일 마감일이 공휴일인 경우)")
    void calculate_weeklyBusinessDay_periodEndBoundaryIsHoliday() {
        // 금요일(08/14) 정산, offset=3이면 원래 화요일(08/11)이 periodEnd여야 하는데
        // 08/11을 공휴일로 주입하면 subtractDays는 그 전 영업일(월, 08/10)로 밀어버린다.
        // 경계일 확장 보정이 없으면 정작 화요일(08/11, 공휴일)에 일한 근무일지가 기간 밖으로 빠진다.
        Platform platform = Platform.builder()
                .settlementCycle("WEEKLY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY").build();
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 8, 11));

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14), holidays);

        // 공휴일이 없을 때와 동일하게 화(08/11)~그 7일 전 수(08/05)여야 한다
        assertEquals(LocalDate.of(2026, 8, 5), period.start());
        assertEquals(LocalDate.of(2026, 8, 11), period.end());
    }

    @Test
    @DisplayName("WEEKLY: settlement_day_of_week가 있으면 정산 기간이 화~금 사이 공휴일로 실제 입금이 다음 영업일로 밀려도 흔들리지 않는다 (쿠팡이츠 실제 사례)")
    void calculate_weekly_dayOfWeekSet_ignoresActualDelayFromHoliday() {
        // 원래는 금요일(08/21) 정산이어야 하는데, 화~금 사이 공휴일 때문에 실제로는
        // 다음 주 월요일(08/24)에 입금됐다고 가정. settlement_day_of_week=FRI가 있으면
        // 실제 입금일(월)이 아니라 그 이전 가장 최근 금요일(08/21)을 기준으로 오프셋을 계산해야
        // 정산 기간(전주 수~금주 화)이 밀리지 않는다.
        Platform platform = Platform.builder()
                .settlementCycle("WEEKLY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY")
                .settlementDayOfWeek("FRI").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 24), Set.of());

        // 원래 예정일(금, 08/21) 기준 3영업일 전 = 화(08/18), 그 7일 전 = 수(08/12)
        assertEquals(LocalDate.of(2026, 8, 12), period.start());
        assertEquals(LocalDate.of(2026, 8, 18), period.end());
    }

    @Test
    @DisplayName("WEEKLY: 실제 입금일이 이미 예정 요일과 같으면(정상 케이스) settlement_day_of_week가 있어도 결과가 그대로다")
    void calculate_weekly_dayOfWeekSet_noDelay_matchesUnsetBehavior() {
        Platform platform = Platform.builder()
                .settlementCycle("WEEKLY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY")
                .settlementDayOfWeek("FRI").build();

        // 08/14은 이미 금요일이라 실제 입금일 == 예정 요일
        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14), Set.of());

        assertEquals(LocalDate.of(2026, 8, 5), period.start());
        assertEquals(LocalDate.of(2026, 8, 11), period.end());
    }
}
