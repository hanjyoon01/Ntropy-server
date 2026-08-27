package com.ntropy.account.port.ai;

/** account-service가 정의한, ai-service의 소비 분류 실행 포트. */
@FunctionalInterface
public interface TransactionClassificationPort {

    /** 특정 사용자의 아직 분석되지 않은 모든 거래를 분류한다. */
    int classifyUnanalyzedTransactions(Long userId);
}
