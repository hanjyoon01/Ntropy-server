package com.ntropy.account.api.dto;

/** account-service가 ACCOUNT 데이터를 집계한 사용자의 금융자산·부채 요약. */
public record FinancialPositionSummary(
        Long liquidAssets,
        Long safeAssets,
        Long totalFinancialAssets,
        Long totalLiabilities,
        Long netFinancialAssets
) {
}
