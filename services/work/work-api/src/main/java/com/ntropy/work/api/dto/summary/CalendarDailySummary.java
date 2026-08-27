package com.ntropy.work.api.dto.summary;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDailySummary {

    private LocalDate date;

    /** 월/화/수/목/금/토/일 */
    private String dayOfWeek;

    private List<CalendarWorkBrief> works;

    /** 피로도 게이지 산출 전이라 당분간 null. */
    private CalendarFatigueGauge fatigue;

    /** 단기예보 범위(3~5일) 밖의 날짜면 null. */
    private WeatherForecast weather;
}
