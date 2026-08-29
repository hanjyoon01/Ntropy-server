package com.ntropy.bff.dto.work.request;

import java.time.LocalTime;

import com.ntropy.work.api.dto.command.JobScheduleCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobScheduleRequest {

    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;

    public JobScheduleCommand toCommand() {
        return new JobScheduleCommand(dayOfWeek, startTime, endTime);
    }
}
