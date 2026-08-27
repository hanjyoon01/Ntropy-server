package com.ntropy.ai.port.account;

/**
 * ai-service가 일간 소비 분류 배치에서 처리할 원본 거래. account-service의
 * DailyClassificationTargetTransaction과 필드 구성은 같지만, ai가 소유한 별개의 타입이다.
 */
public record ClassificationTargetTransaction(
        Long transactionId,
        Long userId,
        String transactionCategory,
        Long outAmount,
        Long inAmount,
        String organizationCode,
        String loanTransactionTypeName,
        String desc1,
        String desc2,
        String desc3,
        String desc4
) {
}
