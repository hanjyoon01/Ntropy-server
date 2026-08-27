package com.ntropy.diagnosis.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
/** diagnosis-service가 방어모드에 전달하는 D-Day 계산용 원본 데이터. */
public class DiagnosisDefenseSnapshot {
    /** 진입 시 즉시 사용할 수 있도록 별도로 확보한 리저브 금액. */
    private Long reserveAmount;

    /** 예금 등 방어기간에 사용할 수 있는 안전자산 금액. */
    private Long safeAssetAmount;

    /** 최근 최대 3개 재무진단 total_expense의 평균. */
    private Long averageMonthlyExpense;

    /** 방어모드가 가용자산과 D-Day를 계산하는 데 필요한 재무 원본 데이터. */
    public DiagnosisDefenseSnapshot(Long reserveAmount, Long safeAssetAmount, Long averageMonthlyExpense) {
        this.reserveAmount = reserveAmount;
        this.safeAssetAmount = safeAssetAmount;
        this.averageMonthlyExpense = averageMonthlyExpense;
    }
}
