package com.ntropy.account.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/** 거래 저장 트랜잭션이 있으면 커밋된 뒤에만 소비 분류 이벤트를 발행합니다. */
@Component
@RequiredArgsConstructor
public class AccountTransactionClassificationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishAfterCommit(Long userId) {
        AccountTransactionsCollectedEvent event =
                new AccountTransactionsCollectedEvent(userId);

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            applicationEventPublisher.publishEvent(event);
                        }
                    }
            );
            return;
        }

        /* 현재 계좌 조합 경로처럼 하위 저장 메서드가 이미 커밋된 경우입니다. */
        applicationEventPublisher.publishEvent(event);
    }
}
