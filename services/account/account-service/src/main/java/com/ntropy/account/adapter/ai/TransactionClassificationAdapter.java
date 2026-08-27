package com.ntropy.account.adapter.ai;

import org.springframework.stereotype.Component;

import com.ntropy.account.port.ai.TransactionClassificationPort;
import com.ntropy.ai.api.client.TransactionClassificationCommandClient;

import lombok.RequiredArgsConstructor;

/** ai-service가 발행한 TransactionClassificationCommandClient를 account의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class TransactionClassificationAdapter implements TransactionClassificationPort {

    private final TransactionClassificationCommandClient transactionClassificationCommandClient;

    @Override
    public int classifyUnanalyzedTransactions(Long userId) {
        return transactionClassificationCommandClient.classifyUnanalyzedTransactions(userId);
    }
}
