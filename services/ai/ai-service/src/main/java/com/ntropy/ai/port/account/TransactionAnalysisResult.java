package com.ntropy.ai.port.account;

/**
 * ai-service가 계산한 거래 1건의 소비 분류 결과. account-service의
 * TransactionAnalysisSaveItem과 필드 구성은 같지만, ai가 소유한 별개의 타입이다.
 */
public record TransactionAnalysisResult(
        Long transactionId,
        Boolean isConsumption,
        String category,
        String expenseType
) {
}
