package com.ntropy.work.adapter.account;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.IncomingTransactionQueryClient;
import com.ntropy.account.api.dto.internal.NormalizedIncomingTransaction;
import com.ntropy.work.port.account.IncomingTransaction;
import com.ntropy.work.port.account.IncomingTransactionPort;

import lombok.RequiredArgsConstructor;

/** account-service가 발행한 IncomingTransactionQueryClient를 work의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class AccountIncomingTransactionAdapter implements IncomingTransactionPort {

    private final IncomingTransactionQueryClient incomingTransactionQueryClient;

    @Override
    public List<IncomingTransaction> findIncomingTransactions(Long userId, LocalDate startDate, LocalDate endDate) {
        return incomingTransactionQueryClient.findIncomingTransactions(userId, startDate, endDate).stream()
                .map(AccountIncomingTransactionAdapter::toPort)
                .toList();
    }

    private static IncomingTransaction toPort(NormalizedIncomingTransaction transaction) {
        return new IncomingTransaction(
                transaction.transactionId(),
                transaction.transactionDate(),
                transaction.transactionTime(),
                transaction.counterpartyName(),
                transaction.amount()
        );
    }
}
