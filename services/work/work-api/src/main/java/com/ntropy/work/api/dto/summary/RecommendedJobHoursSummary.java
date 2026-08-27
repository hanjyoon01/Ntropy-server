package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 잡 한 건에 대한 추천 근무시간 요약 DTO입니다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedJobHoursSummary {
    private Long jobId;
    private String jobName;
    private Long recommendedHours;
    private Long expectedIncome;
    private Integer baseFatigue;
}
