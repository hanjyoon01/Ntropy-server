package com.ntropy.account.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.AccountTransactionAnalysisClient;
import com.ntropy.account.api.dto.ClassificationTargetTransaction;
import com.ntropy.account.api.dto.DailyClassificationTargetTransaction;
import com.ntropy.account.api.dto.TransactionAnalysisSaveItem;
import com.ntropy.account.api.dto.TransactionAnalysisSaveRequest;
import com.ntropy.account.service.TxnAnalysisService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalAccountTransactionAnalysisClient
        implements AccountTransactionAnalysisClient {

    private final TxnAnalysisService txnAnalysisService;

    /**
     * 아직 분석되지 않은 일간 소비 분석 대상 거래를 조회합니다.
     */
    @Override
    public List<DailyClassificationTargetTransaction>
    findUnanalyzedTransactions(int limit) {
        return txnAnalysisService.findUnanalyzedTransactions(limit);
    }

    @Override
    public List<DailyClassificationTargetTransaction>
    findUnanalyzedTransactionsByUserId(Long userId, int limit) {
        return txnAnalysisService.findUnanalyzedTransactionsByUserId(userId, limit);
    }

    /**
     * 일간 배치에서 생성한 소비·비소비 분석 결과를 저장합니다.
     */
    @Override
    public void saveDailyTransactionAnalyses(
            List<TransactionAnalysisSaveItem> analyses
    ) {
        txnAnalysisService.saveDailyAnalyses(analyses);
    }

    /**
     * 기존 월간 소비 분류 흐름에서 사용하는 거래 조회 메서드입니다.
     */
    @Override
    public List<ClassificationTargetTransaction> findClassificationTargets(
            Long userId,
            String yearMonth
    ) {
        return txnAnalysisService.findClassificationTargets(
                userId,
                yearMonth
        );
    }

    /**
     * 기존 월간 소비 분류 흐름에서 사용하는 분석 결과 저장 메서드입니다.
     */
    @Override
    public void saveTransactionAnalyses(
            TransactionAnalysisSaveRequest request
    ) {
        txnAnalysisService.saveAnalyses(request);
    }
}
