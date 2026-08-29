package com.ntropy.account.event;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.ntropy.account.port.ai.TransactionClassificationPort;

import lombok.extern.slf4j.Slf4j;

/** 계좌 연동 응답과 분리된 전용 executor에서 미분류 거래를 처리합니다. */
@Component
@Slf4j
public class AccountTransactionClassificationListener {

    private final TransactionClassificationPort transactionClassificationPort;
    private final TaskExecutor classificationJobExecutor;
    private final Set<Long> runningUserIds = ConcurrentHashMap.newKeySet();

    public AccountTransactionClassificationListener(
            TransactionClassificationPort transactionClassificationPort,
            @Qualifier("accountClassificationJobExecutor") TaskExecutor classificationJobExecutor
    ) {
        this.transactionClassificationPort = transactionClassificationPort;
        this.classificationJobExecutor = classificationJobExecutor;
    }

    @EventListener
    public void onTransactionsCollected(AccountTransactionsCollectedEvent event) {
        Long userId = event.userId();
        if (!runningUserIds.add(userId)) {
            log.info("[비동기 소비 분류] 중복 작업 건너뜀. scope=userId={}", userId);
            return;
        }

        try {
            classificationJobExecutor.execute(() -> classify(userId));
        } catch (RuntimeException e) {
            runningUserIds.remove(userId);
            log.warn(
                    "[비동기 소비 분류] 작업 등록 실패. scope=userId={}, errorKind={}",
                    userId, e.getClass().getSimpleName()
            );
        }
    }

    private void classify(Long userId) {
        long startedAt = System.nanoTime();
        log.info("[비동기 소비 분류] 작업 시작. scope=userId={}", userId);

        try {
            int processed = transactionClassificationPort.classifyUnanalyzedTransactions(userId);
            log.info(
                    "[비동기 소비 분류] 작업 완료. scope=userId={}, totalProcessed={}, elapsedMs={}",
                    userId, processed, elapsedMillis(startedAt)
            );
        } catch (RuntimeException e) {
            log.warn(
                    "[비동기 소비 분류] 작업 실패. scope=userId={}, elapsedMs={}, errorKind={}",
                    userId, elapsedMillis(startedAt), e.getClass().getSimpleName()
            );
        } finally {
            runningUserIds.remove(userId);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
