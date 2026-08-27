package com.ntropy.work.client;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.RecommendedWorkHoursQueryClient;
import com.ntropy.work.api.dto.summary.RecommendedWorkHoursSummary;
import com.ntropy.work.service.RecommendedWorkHoursService;

import lombok.RequiredArgsConstructor;

/** work-service의 계산 서비스를 common 조회 계약으로 노출합니다. */
@Component
@RequiredArgsConstructor
public class LocalRecommendedWorkHoursQueryClient implements RecommendedWorkHoursQueryClient {

    private final RecommendedWorkHoursService service;

    @Override
    public RecommendedWorkHoursSummary getCurrentMonthRecommendedWorkHours(Long userId) {
        return service.getCurrentMonthRecommendedWorkHours(userId);
    }
}

