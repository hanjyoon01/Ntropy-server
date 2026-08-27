package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 월별 소득분석에서 잡별 피로도 집계. averageFatigue는 근무시간 가중평균이다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JobFatigueSummary {

    private Long jobId;
    private String jobName;
    private Integer workDays;
    private Long totalWorkMinutes;
    private Double averageFatigue;
    private Long latestFatigue;
}
