package com.ntropy.work.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

import com.ntropy.work.domain.entity.Platform;

/** 플랫폼 정산 규칙으로 근무일에서 예상 입금일을 순방향 계산한다. */
public final class ExpectedSettlementDateCalculator {

    private static final int DEFAULT_WEEKLY_OFFSET_DAY = 1;
    private static final int WEEKLY_SEARCH_LIMIT_DAYS = 62;
    private static final String BUSINESS_DAY = "BUSINESS_DAY";

    private ExpectedSettlementDateCalculator() {
    }

    public static LocalDate calculate(Platform platform, LocalDate workDate, Set<LocalDate> holidays) {
        if (platform == null || workDate == null) {
            throw new IllegalArgumentException("platform과 workDate가 필요합니다");
        }
        Set<LocalDate> safeHolidays = holidays == null ? Set.of() : holidays;
        return switch (platform.getSettlementCycle()) {
            case "DAILY" -> dailyDate(platform, workDate, safeHolidays);
            case "WEEKLY" -> weeklyDate(platform, workDate, safeHolidays);
            case "MONTHLY" -> monthlyDate(platform, workDate);
            default -> throw new IllegalArgumentException(
                    "알 수 없는 정산 주기입니다: " + platform.getSettlementCycle());
        };
    }

    private static LocalDate dailyDate(Platform platform, LocalDate workDate, Set<LocalDate> holidays) {
        int offset = platform.getSettlementOffsetDay() == null ? 0 : platform.getSettlementOffsetDay();
        return addDays(workDate, offset, platform.getSettlementOffsetUnit(), holidays);
    }

    private static LocalDate weeklyDate(Platform platform, LocalDate workDate, Set<LocalDate> holidays) {
        if (platform.getSettlementDayOfWeek() == null) {
            int offset = platform.getSettlementOffsetDay() == null
                    ? DEFAULT_WEEKLY_OFFSET_DAY
                    : platform.getSettlementOffsetDay();
            return addDays(workDate, offset, platform.getSettlementOffsetUnit(), holidays);
        }

        DayOfWeek scheduledDay = SettlementDayOfWeekParser.parse(platform.getSettlementDayOfWeek());
        LocalDate scheduledDate = workDate.with(TemporalAdjusters.nextOrSame(scheduledDay));
        for (int elapsed = 0; elapsed <= WEEKLY_SEARCH_LIMIT_DAYS; elapsed += 7) {
            LocalDate candidate = scheduledDate.plusDays(elapsed);
            SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, candidate, holidays);
            if (!workDate.isBefore(period.start()) && !workDate.isAfter(period.end())) {
                return resolveWeeklyPaymentDate(candidate, period.end(), platform.getSettlementOffsetUnit(), holidays);
            }
        }
        throw new IllegalStateException(
                "근무일에 대응하는 주간 정산일을 찾지 못했습니다: platformId="
                        + platform.getPlatformId() + ", workDate=" + workDate);
    }

    private static LocalDate monthlyDate(Platform platform, LocalDate workDate) {
        YearMonth paymentMonth = YearMonth.from(workDate).plusMonths(1);
        int configuredDay = platform.getSettlementDayOfMonth() == null ? 1 : platform.getSettlementDayOfMonth();
        int day = Math.min(Math.max(configuredDay, 1), paymentMonth.lengthOfMonth());
        return paymentMonth.atDay(day);
    }

    private static LocalDate addDays(LocalDate date, int days, String unit, Set<LocalDate> holidays) {
        if (!BUSINESS_DAY.equals(unit)) {
            return date.plusDays(days);
        }
        LocalDate result = date;
        int remaining = days;
        while (remaining > 0) {
            result = result.plusDays(1);
            if (isBusinessDay(result, holidays)) {
                remaining--;
            }
        }
        return result;
    }

    private static LocalDate moveToNextBusinessDayIfNeeded(
            LocalDate date, String unit, Set<LocalDate> holidays
    ) {
        if (!BUSINESS_DAY.equals(unit)) {
            return date;
        }
        LocalDate result = date;
        while (!isBusinessDay(result, holidays)) {
            result = result.plusDays(1);
        }
        return result;
    }

    private static LocalDate resolveWeeklyPaymentDate(
            LocalDate scheduledDate,
            LocalDate periodEnd,
            String unit,
            Set<LocalDate> holidays
    ) {
        if (!BUSINESS_DAY.equals(unit)) {
            return scheduledDate;
        }
        boolean holidayBetweenPeriodAndPayment = holidays.stream()
                .anyMatch(holiday -> holiday.isAfter(periodEnd) && !holiday.isAfter(scheduledDate));
        if (holidayBetweenPeriodAndPayment) {
            return moveToNextBusinessDayIfNeeded(scheduledDate.plusDays(1), unit, holidays);
        }
        return moveToNextBusinessDayIfNeeded(scheduledDate, unit, holidays);
    }

    private static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

}
