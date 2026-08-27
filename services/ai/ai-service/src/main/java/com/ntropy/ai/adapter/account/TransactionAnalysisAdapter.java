package com.ntropy.ai.adapter.account;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.AccountTransactionAnalysisClient;
import com.ntropy.account.api.dto.DailyClassificationTargetTransaction;
import com.ntropy.account.api.dto.TransactionAnalysisSaveItem;
import com.ntropy.ai.port.account.ClassificationTargetTransaction;
import com.ntropy.ai.port.account.TransactionAnalysisPort;
import com.ntropy.ai.port.account.TransactionAnalysisResult;

import lombok.RequiredArgsConstructor;

/** account-service가 발행한 AccountTransactionAnalysisClient를 ai의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class TransactionAnalysisAdapter implements TransactionAnalysisPort {

    private final AccountTransactionAnalysisClient accountTransactionAnalysisClient;

    @Override
    public List<ClassificationTargetTransaction> findUnanalyzedTransactions(int limit) {
        return accountTransactionAnalysisClient.findUnanalyzedTransactions(limit).stream()
                .map(TransactionAnalysisAdapter::toPort)
                .toList();
    }

    @Override
    public List<ClassificationTargetTransaction> findUnanalyzedTransactionsByUserId(Long userId, int limit) {
        return accountTransactionAnalysisClient.findUnanalyzedTransactionsByUserId(userId, limit).stream()
                .map(TransactionAnalysisAdapter::toPort)
                .toList();
    }

    @Override
    public void saveDailyTransactionAnalyses(List<TransactionAnalysisResult> analyses) {
        List<TransactionAnalysisSaveItem> items = analyses.stream()
                .map(result -> new TransactionAnalysisSaveItem(
                        result.transactionId(), result.isConsumption(), result.category(), result.expenseType()))
                .toList();
        accountTransactionAnalysisClient.saveDailyTransactionAnalyses(items);
    }

    private static ClassificationTargetTransaction toPort(DailyClassificationTargetTransaction target) {
        return new ClassificationTargetTransaction(
                target.getTransactionId(),
                target.getUserId(),
                target.getTransactionCategory(),
                target.getOutAmount(),
                target.getInAmount(),
                target.getOrganizationCode(),
                target.getLoanTransactionTypeName(),
                target.getDesc1(),
                target.getDesc2(),
                target.getDesc3(),
                target.getDesc4()
        );
    }
}
