package com.ntropy.bff.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * actualIncome: 이번 달 실제 입금액. SETTLEMENT(MATCHED, deposit_date 기준) 합계 - 진짜 통장에 들어온 돈.
 * expectedSettlementIncome: 예상 정산 소득. WORK_LOG/WORK_LOG_PLATFORM_INCOME 기준 아직 정산 안 된 예상치.
 * goalIncome: 이번 달 SAVING_GOAL.target_amount.
 */
@Getter
@AllArgsConstructor
public class DashboardIncomeResponse {

    private Long actualIncome;
    private Long expectedSettlementIncome;
    private Long goalIncome;
}
