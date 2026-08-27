package com.ntropy.work.domain;

import java.time.DayOfWeek;
import java.util.Locale;

/** PLATFORM.settlement_day_of_week의 MON~SUN 값을 Java 요일로 변환한다. */
public final class SettlementDayOfWeekParser {

    private SettlementDayOfWeekParser() {
    }

    public static DayOfWeek parse(String abbreviation) {
        if (abbreviation == null) {
            throw new IllegalArgumentException("settlement_day_of_week 값이 필요합니다");
        }
        return switch (abbreviation.toUpperCase(Locale.ROOT)) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException(
                    "알 수 없는 settlement_day_of_week 값입니다: " + abbreviation);
        };
    }
}
