package com.ntropy.work.mapper.projection;

import java.time.LocalDate;

import com.ntropy.work.domain.enums.SettlementStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 가상 정산기간의 누적 목표액 계산에 필요한 확정 근무일지의 플랫폼별 소득. */
@Getter
@Setter
@NoArgsConstructor
public class VirtualSettlementIncome {

    private Long incomeId;
    private Long userId;
    private Long platformId;
    private LocalDate workDate;
    private Long expectedAmount;
    private SettlementStatus settlementStatus;
}
