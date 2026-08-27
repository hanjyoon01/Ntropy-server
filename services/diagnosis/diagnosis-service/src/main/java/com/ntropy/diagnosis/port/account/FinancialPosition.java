package com.ntropy.diagnosis.port.account;

/**
 * diagnosis-service가 재무진단에 사용하는 사용자 금융자산 요약. account-service의
 * FinancialPositionSummary와 필드 구성은 비슷하지만, diagnosis가 소유한 별개의 타입이다.
 */
public record FinancialPosition(
        Long totalFinancialAssets,
        Long liquidAssets,
        Long safeAssets
) {
}
