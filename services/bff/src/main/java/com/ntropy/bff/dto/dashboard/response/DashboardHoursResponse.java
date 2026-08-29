package com.ntropy.bff.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardHoursResponse {

    private int confirmedHours;
    private int scheduledHours;
    private int goalHours;
}
