package com.ntropy.work.api.client;

import com.ntropy.work.api.dto.summary.RecommendedWorkHoursSummary;

/** work-service가 계산한 이번 달 추천 근무시간을 조회하는 모듈 간 계약입니다. */
public interface RecommendedWorkHoursQueryClient {

    /** 목표가 없으면 null, 있으면 저장 또는 계산된 이번 달 결과를 반환합니다. */
    RecommendedWorkHoursSummary getCurrentMonthRecommendedWorkHours(Long userId);
}
