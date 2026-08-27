package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.Platform;

class ExpectedSettlementDateCalculatorTest {

    @Test
    @DisplayName("배민커넥트는 근무일에서 3영업일 뒤를 예상 정산일로 계산한다")
    void dailyBusinessDay_addsOffsetForward() {
        Platform platform = Platform.builder()
                .settlementCycle("DAILY")
                .settlementOffsetDay(3)
                .settlementOffsetUnit("BUSINESS_DAY")
                .build();

        LocalDate result = ExpectedSettlementDateCalculator.calculate(
                platform, LocalDate.of(2026, 7, 16), Set.of());

        assertEquals(LocalDate.of(2026, 7, 21), result);
    }

    @Test
    @DisplayName("영업일 계산은 주말과 주입된 공휴일을 건너뛴다")
    void dailyBusinessDay_skipsWeekendAndHoliday() {
        Platform platform = Platform.builder()
                .settlementCycle("DAILY")
                .settlementOffsetDay(3)
                .settlementOffsetUnit("BUSINESS_DAY")
                .build();

        LocalDate result = ExpectedSettlementDateCalculator.calculate(
                platform,
                LocalDate.of(2026, 7, 16),
                Set.of(LocalDate.of(2026, 7, 20)));

        assertEquals(LocalDate.of(2026, 7, 22), result);
    }

    @Test
    @DisplayName("쿠팡이츠는 전주 수요일부터 이번주 화요일 근무분을 이번주 금요일로 계산한다")
    void weekly_resolvesContainingSettlementPeriod() {
        Platform platform = Platform.builder()
                .platformId(2L)
                .settlementCycle("WEEKLY")
                .settlementOffsetDay(3)
                .settlementOffsetUnit("BUSINESS_DAY")
                .settlementDayOfWeek("FRI")
                .build();

        LocalDate result = ExpectedSettlementDateCalculator.calculate(
                platform, LocalDate.of(2026, 7, 16), Set.of());

        assertEquals(LocalDate.of(2026, 7, 24), result);
    }

    @Test
    @DisplayName("주간 정산기간과 예정일 사이에 공휴일이 있으면 입금일만 다음 영업일로 미룬다")
    void weeklyHoliday_delaysPaymentWithoutChangingPeriod() {
        Platform platform = Platform.builder()
                .platformId(2L)
                .settlementCycle("WEEKLY")
                .settlementOffsetDay(3)
                .settlementOffsetUnit("BUSINESS_DAY")
                .settlementDayOfWeek("FRI")
                .build();

        LocalDate result = ExpectedSettlementDateCalculator.calculate(
                platform,
                LocalDate.of(2026, 7, 16),
                Set.of(LocalDate.of(2026, 7, 22)));

        assertEquals(LocalDate.of(2026, 7, 27), result);
        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, result,
                Set.of(LocalDate.of(2026, 7, 22)));
        assertEquals(LocalDate.of(2026, 7, 15), period.start());
        assertEquals(LocalDate.of(2026, 7, 21), period.end());
    }

    @Test
    @DisplayName("월 정산은 근무월의 다음 달 지정 일자로 계산한다")
    void monthly_usesConfiguredDayInNextMonth() {
        Platform platform = Platform.builder()
                .settlementCycle("MONTHLY")
                .settlementDayOfMonth(21)
                .build();

        LocalDate result = ExpectedSettlementDateCalculator.calculate(
                platform, LocalDate.of(2026, 7, 16), Set.of());

        assertEquals(LocalDate.of(2026, 8, 21), result);
    }
}
