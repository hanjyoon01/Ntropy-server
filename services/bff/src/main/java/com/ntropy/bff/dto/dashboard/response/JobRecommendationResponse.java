package com.ntropy.bff.dto.dashboard.response;

import com.ntropy.work.api.dto.summary.RecommendedJobHoursSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobRecommendationResponse {

    private Long jobId;
    private String jobName;
    private Long currentHours;
    private Long recommendedHours;
    private Long expectedIncome;
    private Integer baseFatigue;

    /** currentHours는 이 잡으로 이번 달 지금까지 일한 시간(분→시간 환산). 데이터 없으면 0. */
    public static JobRecommendationResponse from(RecommendedJobHoursSummary summary, Long currentHours) {
        return new JobRecommendationResponse(
                summary.getJobId(),
                summary.getJobName(),
                currentHours == null ? 0L : currentHours,
                summary.getRecommendedHours(),
                summary.getExpectedIncome(),
                summary.getBaseFatigue()
        );
    }
}
