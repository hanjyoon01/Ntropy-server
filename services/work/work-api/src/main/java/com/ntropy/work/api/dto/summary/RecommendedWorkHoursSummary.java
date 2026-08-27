package com.ntropy.work.api.dto.summary;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 외부 서비스에 노출할 이번 달 추천 근무시간 응답 DTO입니다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedWorkHoursSummary {
    private String targetMonth;
    private Long totalRecommendedHours;
    private List<RecommendedJobHoursSummary> recommendedJobs;
}
