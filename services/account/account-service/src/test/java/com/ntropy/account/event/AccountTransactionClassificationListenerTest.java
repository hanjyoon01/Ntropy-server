package com.ntropy.account.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import com.ntropy.account.port.ai.TransactionClassificationPort;

class AccountTransactionClassificationListenerTest {

    @Test
    void schedulesClassificationWithoutRunningItOnPublisherThread() {
        RecordingExecutor executor = new RecordingExecutor();
        RecordingClassificationClient classificationClient = new RecordingClassificationClient();
        AccountTransactionClassificationListener listener =
                new AccountTransactionClassificationListener(classificationClient, executor);

        listener.onTransactionsCollected(new AccountTransactionsCollectedEvent(42L));

        assertTrue(classificationClient.userIds.isEmpty());
        assertEquals(1, executor.tasks.size());

        executor.runNext();

        assertEquals(List.of(42L), classificationClient.userIds);
    }

    @Test
    void ignoresDuplicateJobForSameUserUntilRunningJobFinishes() {
        RecordingExecutor executor = new RecordingExecutor();
        RecordingClassificationClient classificationClient = new RecordingClassificationClient();
        AccountTransactionClassificationListener listener =
                new AccountTransactionClassificationListener(classificationClient, executor);
        AccountTransactionsCollectedEvent event = new AccountTransactionsCollectedEvent(42L);

        listener.onTransactionsCollected(event);
        listener.onTransactionsCollected(event);

        assertEquals(1, executor.tasks.size());

        executor.runNext();
        listener.onTransactionsCollected(event);

        assertEquals(1, executor.tasks.size());
        executor.runNext();
        assertEquals(List.of(42L, 42L), classificationClient.userIds);
    }

    @Test
    void keepsListenerSuccessfulWhenBackgroundClassificationFails() {
        RecordingClassificationClient classificationClient = new RecordingClassificationClient();
        classificationClient.failure = new IllegalStateException("분류 실패");
        AccountTransactionClassificationListener listener =
                new AccountTransactionClassificationListener(classificationClient, Runnable::run);

        assertDoesNotThrow(
                () -> listener.onTransactionsCollected(
                        new AccountTransactionsCollectedEvent(42L)
                )
        );
        assertEquals(List.of(42L), classificationClient.userIds);
    }

    @Test
    void releasesDuplicateGuardWhenExecutorRejectsJob() {
        RecordingClassificationClient classificationClient = new RecordingClassificationClient();
        TaskExecutor rejectingExecutor = task -> {
            throw new TaskRejectedException("포화");
        };
        AccountTransactionClassificationListener listener =
                new AccountTransactionClassificationListener(classificationClient, rejectingExecutor);

        assertDoesNotThrow(
                () -> listener.onTransactionsCollected(
                        new AccountTransactionsCollectedEvent(42L)
                )
        );
        assertDoesNotThrow(
                () -> listener.onTransactionsCollected(
                        new AccountTransactionsCollectedEvent(42L)
                )
        );
    }

    private static class RecordingExecutor implements TaskExecutor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runNext() {
            tasks.remove(0).run();
        }
    }

    private static class RecordingClassificationClient
            implements TransactionClassificationPort {
        private final List<Long> userIds = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public int classifyUnanalyzedTransactions(Long userId) {
            userIds.add(userId);
            if (failure != null) {
                throw failure;
            }
            return 1;
        }
    }

}
