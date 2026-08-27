package com.ntropy.defense.port.diagnosis;

/**
 * defense-service가 D-Day를 계산하는 데 필요로 하는 재무 원본 데이터.
 * diagnosis-service의 DiagnosisDefenseSnapshot과 필드 구성은 같지만, defense가 소유한
 * 별개의 타입이다.
 */
public record DefenseDiagnosisSnapshot(
        Long reserveAmount,
        Long safeAssetAmount,
        Long averageMonthlyExpense
) {
}
