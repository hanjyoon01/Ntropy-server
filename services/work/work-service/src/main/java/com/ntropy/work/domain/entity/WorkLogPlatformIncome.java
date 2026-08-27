package com.ntropy.work.domain.entity;

import com.ntropy.work.domain.enums.SettlementStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 근무일지 1건의 소득을 플랫폼별로 분배한 행. 한 근무에 플랫폼을 여러 개 동시 운영하는
 * 경우(배달 등)에도 WORK_LOG 자체는 쪼개지 않고, 소득/정산 상태만 이 테이블에서
 * 플랫폼 단위로 추적한다. WorkLog.settlementStatus는 이 행들로부터 파생 계산된다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkLogPlatformIncome {

    private Long incomeId;
    private Long logId;
    private Long platformId;
    private Long expectedAmount;
    private SettlementStatus settlementStatus;
}
