package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * goalHours: 해당 월 ALLOCATION_GOAL(잡별 추천 근무시간) 합
 * confirmedHours/scheduledHours: 해당 월 WORK_LOG를 status(CONFIRMED/PLANNED) 기준으로 나눈 근무시간 합
 * expectedIncome: 전체 WORK_LOG의 estimated_income 합 (status/settlementStatus 무관)
 * expectedSettlementIncome: 예상 정산 소득. WORK_LOG_PLATFORM_INCOME 기준으로 아직 COMPLETED가 안 된
 *   플랫폼 몫 + PLANNED WORK_LOG 전체(estimated_income). 실제 입금액(actualIncome)은 SETTLEMENT
 *   테이블 기반이라 WorkLog 데이터만으로는 계산할 수 없어 이 DTO에는 포함하지 않는다 - 필요하면
 *   IncomeAnalysisQueryClient(§ SETTLEMENT 집계)를 별도로 조회해야 한다.
 * targetAmount: 해당 월 SAVING_GOAL.target_amount. 저축 목표를 등록하지 않은 달은 null
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthlyHours {

    private int goalHours;
    private int confirmedHours;
    private int scheduledHours;
    private Long expectedIncome;
    private Long expectedSettlementIncome;
    private Long targetAmount;
}
