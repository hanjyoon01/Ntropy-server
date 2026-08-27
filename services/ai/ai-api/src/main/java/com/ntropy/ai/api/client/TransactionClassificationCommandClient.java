package com.ntropy.ai.api.client;

/** 계좌 거래 수집 완료 후 사용자 단위 소비 분류를 실행하는 내부 계약입니다. */
public interface TransactionClassificationCommandClient {

    /** 특정 사용자의 아직 분석되지 않은 모든 거래를 분류합니다. */
    int classifyUnanalyzedTransactions(Long userId);
}
