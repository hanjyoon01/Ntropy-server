package com.ntropy.work.domain;

import java.util.List;

import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementStatus;

/**
 * WorkLog.settlementStatus는 이제 그 근무일지에 딸린 WORK_LOG_PLATFORM_INCOME 행들의
 * 상태로부터 파생 계산된다 (WorkLogService의 확정 시점, SettlementService의 배치
 * 매칭 시점 양쪽에서 공통으로 쓰기 위해 분리).
 */
public final class WorkLogSettlementStatusCalculator {

    private WorkLogSettlementStatusCalculator() {
    }

    public static SettlementStatus calculate(List<WorkLogPlatformIncome> incomes) {
        if (incomes.isEmpty()) {
            // 매핑된 플랫폼이 없어 income 행 자체를 못 만든 경우 - 추적 불가하니 PENDING 유지
            return SettlementStatus.PENDING;
        }
        boolean allCompleted = incomes.stream()
                .allMatch(income -> income.getSettlementStatus() == SettlementStatus.COMPLETED);
        if (allCompleted) {
            return SettlementStatus.COMPLETED;
        }
        boolean anyCompleted = incomes.stream()
                .anyMatch(income -> income.getSettlementStatus() == SettlementStatus.COMPLETED);
        return anyCompleted ? SettlementStatus.PARTIAL : SettlementStatus.PENDING;
    }
}
