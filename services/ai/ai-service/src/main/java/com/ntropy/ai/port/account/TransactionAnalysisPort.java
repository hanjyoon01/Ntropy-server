package com.ntropy.ai.port.account;

import java.util.List;

/** ai-service가 정의한, account-service의 거래 분석 조회·저장 포트. */
public interface TransactionAnalysisPort {

    List<ClassificationTargetTransaction> findUnanalyzedTransactions(int limit);

    List<ClassificationTargetTransaction> findUnanalyzedTransactionsByUserId(Long userId, int limit);

    void saveDailyTransactionAnalyses(List<TransactionAnalysisResult> analyses);
}
