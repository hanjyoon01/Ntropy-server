package com.ntropy.work.api.client;

import com.ntropy.work.api.dto.summary.SavingGoalSummary;

/**
 * work-service의 SAVING_GOAL 조회 계약. work-service가 LocalSavingGoalQueryClient로 구현하고,
 * bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface SavingGoalQueryClient {

    /** 이번 달(서버 시간 기준) 저축목표를 조회한다. 등록된 게 없으면 null. */
    SavingGoalSummary findCurrentMonthGoal(Long userId);
}
