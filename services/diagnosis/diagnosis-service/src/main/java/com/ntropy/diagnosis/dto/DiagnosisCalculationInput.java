package com.ntropy.diagnosis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재무 진단 계산에 필요한 입력 데이터입니다.
 *
 * work-service와 account-service에서 전달받은 값을
 * diagnosis-service가 계산하기 쉬운 형태로 묶습니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisCalculationInput {

    /** 진단 대상 사용자 ID */
    private Long userId;

    /** 진단 기준 연월: YYYY-MM */
    private String yearMonth;

    /** MATCHED 상태의 총소득 */
    private Long totalIncome;

    /** UNMATCHED 상태의 소득 */
    private Long unmatchedIncome;

    /** 소비로 분류된 총지출 */
    private Long totalExpense;

    /** 고정지출 합계 */
    private Long fixedExpense;

    /** 전체 금융자산 */
    private Long totalFinancialAssets;

    /** 유동자산 */
    private Long liquidAssets;

    /** 안전자산 */
    private Long safeAssets;
}