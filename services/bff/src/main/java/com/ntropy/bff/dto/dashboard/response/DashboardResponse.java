package com.ntropy.bff.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardResponse {

    private String greetingName;
    private DashboardHoursResponse goalHours;
    private DashboardIncomeResponse goalIncome;
    private Integer fatigueScore;
}
