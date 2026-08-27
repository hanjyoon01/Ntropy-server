package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ntropy.ai.client.fastapi.FastApiTransactionClassificationClient;
import com.ntropy.ai.dto.fastapi.TransactionClassificationData;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResponse;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResult;
import com.ntropy.ai.dto.fastapi.TransactionForClassification;
import com.ntropy.ai.port.account.ClassificationTargetTransaction;
import com.ntropy.ai.port.account.TransactionAnalysisPort;
import com.ntropy.ai.port.account.TransactionAnalysisResult;

class DailyTransactionClassificationServiceTest {

    @Test
    void savesDeterministicAndFastApiResultsAndUsesFallbackForMissingResult() {
        FakeAccountClient accountClient =
                new FakeAccountClient(
                        List.of(
                                target(
                                        1L,
                                        "ORDINARY",
                                        "삼성생명 실손보험"
                                ),
                                target(
                                        2L,
                                        "ORDINARY",
                                        "스타벅스"
                                ),
                                target(
                                        3L,
                                        "ORDINARY",
                                        "알 수 없는 상점"
                                )
                        )
                );

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();

        /*
         * FastAPI가 2번 거래 결과만 반환하도록 구성합니다.
         * 누락된 3번 거래는 ETC / VARIABLE로 저장되어야 합니다.
         */
        fastApiClient.results = List.of(
                new TransactionClassificationResult(
                        2L,
                        true,
                        "FOOD",
                        "VARIABLE"
                )
        );

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        fastApiClient,
                        new TransactionPreClassificationService(),
                        Runnable::run
                );

        assertEquals(
                3,
                service.run()
        );

        /*
         * 보험료는 Spring에서 결정되므로 FastAPI에는
         * 나머지 두 거래만 전달됩니다.
         */
        assertEquals(
                2,
                fastApiClient.requested.size()
        );

        assertSaved(
                accountClient.saved,
                1L,
                "INSURANCE",
                "FIXED"
        );

        assertSaved(
                accountClient.saved,
                2L,
                "FOOD",
                "VARIABLE"
        );

        assertSaved(
                accountClient.saved,
                3L,
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void splitsFastApiRequestsIntoBatchesOfAtMostOneHundred() {
        List<ClassificationTargetTransaction> targets =
                new ArrayList<>();

        for (long id = 1; id <= 101; id++) {
            targets.add(
                    target(
                            id,
                            "ORDINARY",
                            "알 수 없는 상점 " + id
                    )
            );
        }

        FakeAccountClient accountClient =
                new FakeAccountClient(targets);

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();
        CountingExecutor executor = new CountingExecutor();

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        fastApiClient,
                        new TransactionPreClassificationService(),
                        executor
                );

        assertEquals(
                101,
                service.run()
        );

        assertEquals(
                List.of(100, 1),
                fastApiClient.requestSizes
        );
        assertEquals(2, executor.executions);

        assertEquals(
                101,
                accountClient.saved.size()
        );
    }

    @Test
    void runsFastApiBatchesConcurrentlyOnTheConfiguredExecutor() {
        List<DailyClassificationTargetTransaction> targets = new ArrayList<>();
        for (long id = 1; id <= 101; id++) {
            targets.add(target(id, "ORDINARY", "알 수 없는 상점 " + id));
        }

        ConcurrentFastApiClient fastApiClient = new ConcurrentFastApiClient();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            DailyTransactionClassificationService service =
                    new DailyTransactionClassificationService(
                            new FakeAccountClient(targets),
                            fastApiClient,
                            new TransactionPreClassificationService(),
                            executor
                    );

            assertEquals(101, service.run());
            assertEquals(2, fastApiClient.maxConcurrentRequests.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidFastApiResultUsesFallback() {
        FakeAccountClient accountClient =
                new FakeAccountClient(
                        List.of(
                                target(
                                        1L,
                                        "ORDINARY",
                                        "알 수 없는 상점"
                                )
                        )
                );

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();

        /*
         * 허용하지 않는 category와 expenseType을 반환하면
         * 해당 응답을 신뢰하지 않고 fallback합니다.
         */
        fastApiClient.results = List.of(
                new TransactionClassificationResult(
                        1L,
                        true,
                        "UNKNOWN",
                        "SOMETIMES"
                )
        );

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        fastApiClient,
                        new TransactionPreClassificationService(),
                        Runnable::run
                );

        service.run();

        assertSaved(
                accountClient.saved,
                1L,
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void continuesUntilAccountServiceReturnsNoMorePages() {
        FakeAccountClient accountClient =
                new FakeAccountClient(
                        List.of(
                                List.of(
                                        target(
                                                1L,
                                                "INSTALLMENT",
                                                "정기적금"
                                        )
                                ),
                                List.of(
                                        target(
                                                2L,
                                                "LOAN",
                                                "대출상환"
                                        )
                                )
                        ),
                        true
                );

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        accountClient,
                        new FakeFastApiClient(),
                        new TransactionPreClassificationService(),
                        Runnable::run
                );

        assertEquals(
                2,
                service.run()
        );

        /*
         * 첫 페이지, 두 번째 페이지, 종료 확인까지 총 세 번 조회합니다.
         */
        assertEquals(
                3,
                accountClient.queryCalls
        );

        assertEquals(
                2,
                accountClient.saved.size()
        );
    }

    @Test
    void classifiesOnlyTheRequestedUsersUnanalyzedTransactions() {
        FakeAccountClient accountClient = new FakeAccountClient(
                List.of(target(1L, "ORDINARY", "스타벅스"))
        );
        DailyTransactionClassificationService service = new DailyTransactionClassificationService(
                accountClient, new FakeFastApiClient(), new TransactionPreClassificationService(), Runnable::run
        );

        assertEquals(1, service.classifyUnanalyzedTransactions(42L));
        assertEquals(List.of(42L, 42L), accountClient.queriedUserIds);
    }

    private void assertSaved(
            List<TransactionAnalysisResult> saved,
            Long transactionId,
            String expectedCategory,
            String expectedExpenseType
    ) {
        TransactionAnalysisResult item = saved.stream()
                .filter(
                        value -> transactionId.equals(
                                value.transactionId()
                        )
                )
                .findFirst()
                .orElseThrow();

        assertEquals(
                expectedCategory,
                item.category()
        );

        assertEquals(
                expectedExpenseType,
                item.expenseType()
        );
    }

    private ClassificationTargetTransaction target(
            Long transactionId,
            String transactionCategory,
            String desc3
    ) {
        long outAmount =
                "INSTALLMENT".equals(transactionCategory)
                        ? 0L
                        : 10_000L;

        long inAmount =
                "INSTALLMENT".equals(transactionCategory)
                        ? 10_000L
                        : 0L;

        return new ClassificationTargetTransaction(
                transactionId,
                10L,
                transactionCategory,
                outAmount,
                inAmount,
                "0004",
                null,
                null,
                "체크",
                desc3,
                null
        );
    }

    private static class FakeFastApiClient
            extends FastApiTransactionClassificationClient {

        private List<TransactionForClassification> requested =
                List.of();

        private List<TransactionClassificationResult> results =
                List.of();

        private final List<Integer> requestSizes =
                new ArrayList<>();

        @Override
        public TransactionClassificationResponse classifyTransactions(
                List<TransactionForClassification> transactions
        ) {
            requested = List.copyOf(transactions);
            requestSizes.add(transactions.size());

            return new TransactionClassificationResponse(
                    true,
                    200,
                    "ok",
                    new TransactionClassificationData(results)
            );
        }
    }

    private static class CountingExecutor implements Executor {
        private int executions;

        @Override
        public void execute(Runnable command) {
            executions++;
            command.run();
        }
    }

    private static class ConcurrentFastApiClient
            extends FastApiTransactionClassificationClient {
        private final CountDownLatch bothRequestsStarted = new CountDownLatch(2);
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final AtomicInteger maxConcurrentRequests = new AtomicInteger();

        @Override
        public TransactionClassificationResponse classifyTransactions(
                List<TransactionForClassification> transactions
        ) {
            int active = activeRequests.incrementAndGet();
            maxConcurrentRequests.accumulateAndGet(active, Math::max);
            bothRequestsStarted.countDown();
            try {
                if (!bothRequestsStarted.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("두 FastAPI 배치가 동시에 시작되지 않았습니다.");
                }
                return new TransactionClassificationResponse(
                        true, 200, "ok", new TransactionClassificationData(List.of())
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                activeRequests.decrementAndGet();
            }
        }
    }

    private static class FakeAccountClient
            implements TransactionAnalysisPort {

        private final List<
                List<ClassificationTargetTransaction>
                > pages;

        private int pageIndex;
        private int queryCalls;
        private final List<Long> queriedUserIds = new ArrayList<>();

        private final List<TransactionAnalysisResult> saved =
                new ArrayList<>();

        private FakeAccountClient(
                List<ClassificationTargetTransaction> nextPage
        ) {
            this.pages = List.of(
                    List.copyOf(nextPage)
            );
        }

        private FakeAccountClient(
                List<List<ClassificationTargetTransaction>> pages,
                boolean multiplePages
        ) {
            this.pages = pages.stream()
                    .map(List::copyOf)
                    .toList();
        }

        @Override
        public List<ClassificationTargetTransaction>
        findUnanalyzedTransactions(int limit) {
            queryCalls++;

            if (pageIndex >= pages.size()) {
                return List.of();
            }

            return pages.get(pageIndex++);
        }

        @Override
        public List<ClassificationTargetTransaction>
        findUnanalyzedTransactionsByUserId(Long userId, int limit) {
            queriedUserIds.add(userId);
            return findUnanalyzedTransactions(limit);
        }

        @Override
        public void saveDailyTransactionAnalyses(
                List<TransactionAnalysisResult> analyses
        ) {
            saved.addAll(analyses);
        }
    }
}
