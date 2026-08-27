package com.ntropy.diagnosis.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DIAGNOSIS_RESULT 테이블과 매핑되는 월별 재무 진단 도메인 객체입니다.
 *
 * 사용자·연월별 재무 상태를 하나의 스냅샷으로 저장합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResult {

    /** 진단 결과 PK */
    private Long diagnosisId;

    /** 진단 대상 사용자 ID */
    private Long userId;

    /** 진단 기준 연월: YYYY-MM */
    private String yearMonth;

    /** 잡 매칭이 완료된 총소득 */
    private Long totalIncome;

    /** 잡과 매칭되지 않은 입금 합계 */
    private Long unmatchedIncome;

    /** 소비로 분류된 총지출 */
    private Long totalExpense;

    /** 총소득 - 총소비 */
    private Long netCashFlow;

    /** 고정지출 합계 */
    private Long fixedExpense;

    /** 고정지출 / 총소득 */
    private BigDecimal fixedExpenseRatio;

    /** 전체 금융자산 */
    private Long totalFinancialAssets;

    /** 즉시 사용할 수 있는 유동자산 */
    private Long liquidAssets;

    /** 방어기간에 사용할 수 있는 안전자산 */
    private Long safeAssets;

    /** 최근 진단 계산 완료 시각 */
    private LocalDateTime calculatedAt;

    /** 월말 기준으로 확정된 시각. 진행 중인 현재 월은 null이다. */
    private LocalDateTime finalizedAt;

    /** 최초 생성 시각 */
    private LocalDateTime createdAt;

    /** 마지막 수정 시각 */
    private LocalDateTime updatedAt;
}