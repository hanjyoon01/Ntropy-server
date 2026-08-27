package com.ntropy.work.api.dto.summary;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDaySummary {

    private LocalDate date;

    /** 해당 날짜의 WORK_LOG가 전부 COMPLETED면 COMPLETED, 하나라도 아니면 PENDING. */
    private String settlementStatus;

    private List<CalendarJobBrief> jobs;

    /** 단기예보 범위(3~5일) 밖의 날짜면 null. */
    private WeatherForecast weather;
}
