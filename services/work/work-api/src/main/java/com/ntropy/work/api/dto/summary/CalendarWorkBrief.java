package com.ntropy.work.api.dto.summary;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarWorkBrief {

    private Long workId;
    private Long jobId;
    private String jobName;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String settlementStatus;
    private Long taskCount;
    private Long fatigue;
}
