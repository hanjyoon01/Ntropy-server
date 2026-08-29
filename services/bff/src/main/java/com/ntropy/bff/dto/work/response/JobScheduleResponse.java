package com.ntropy.bff.dto.work.response;

import java.time.LocalTime;

import com.ntropy.work.api.dto.summary.JobScheduleSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobScheduleResponse {

    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;

    public static JobScheduleResponse from(JobScheduleSummary summary) {
        JobScheduleResponse response = new JobScheduleResponse();
        response.dayOfWeek = summary.getDayOfWeek();
        response.startTime = summary.getStartTime();
        response.endTime = summary.getEndTime();
        return response;
    }
}
