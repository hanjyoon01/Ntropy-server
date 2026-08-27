package com.ntropy.work.api.dto.summary;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthlySummary {

    private int year;
    private int month;
    private CalendarMonthlyHours summary;
    private List<CalendarDaySummary> days;
}
