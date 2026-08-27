package com.ntropy.work.api.dto.command;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 잡 등록 시 같이 등록하는 정기근무 스케줄 한 건.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JobScheduleCommand {

    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
}
