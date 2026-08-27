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
        FakeTransactionAnalysisPort transactionAnalysisPort =
                new FakeTransactionAnalysisPort(
                        List.of(
                                target(
                                        1L,
                                        "ORDINARY",
                                        "삼성생명 자동차보험"
                                ),
                                target(
                                        2L,
                                        "ORDINARY",
                                        "스타벅스"
                                ),
                                target(
                                        3L,
                                        "ORDINARY",
                                        "규칙에 없는 가맹점"
                                )
                        )
                );

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();

        /*
         * FastAPI가 2번 거래 결과만 반환하도록 구성합니다.
         * 나머지 3번 거래는 ETC / VARIABLE로 저장되어야 합니다.
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
                        transactionAnalysisPort,
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
                transactionAnalysisPort.saved,
                1L,
                "INSURANCE",
                "FIXED"
        );

        assertSaved(
                transactionAnalysisPort.saved,
                2L,
                "FOOD",
                "VARIABLE"
        );

        assertSaved(
                transactionAnalysisPort.saved,
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
                            "규칙에 없는 가맹점 " + id
                    )
            );
        }

        FakeTransactionAnalysisPort transactionAnalysisPort =
                new FakeTransactionAnalysisPort(targets);

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();
        CountingExecutor executor = new CountingExecutor();

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        transactionAnalysisPort,
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
                transactionAnalysisPort.saved.size()
        );
    }

    @Test
    void runsFastApiBatchesConcurrentlyOnTheConfiguredExecutor() {
        List<ClassificationTargetTransaction> targets = new ArrayList<>();
        for (long id = 1; id <= 101; id++) {
            targets.add(target(id, "ORDINARY", "규칙에 없는 가맹점 " + id));
        }

        ConcurrentFastApiClient fastApiClient = new ConcurrentFastApiClient();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            DailyTransactionClassificationService service =
                    new DailyTransactionClassificationService(
                            new FakeTransactionAnalysisPort(targets),
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
        FakeTransactionAnalysisPort transactionAnalysisPort =
                new FakeTransactionAnalysisPort(
                        List.of(
                                target(
                                        1L,
                                        "ORDINARY",
                                        "규칙에 없는 가맹점"
                                )
                        )
                );

        FakeFastApiClient fastApiClient =
                new FakeFastApiClient();

        /*
         * 허용되지 않는 category와 expenseType을 반환하면
         * 해당 응답은 신뢰하지 않고 fallback합니다.
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
                        transactionAnalysisPort,
                        fastApiClient,
                        new TransactionPreClassificationService(),
                        Runnable::run
                );

        service.run();

        assertSaved(
                transactionAnalysisPort.saved,
                1L,
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void continuesUntilAccountServiceReturnsNoMorePages() {
        FakeTransactionAnalysisPort transactionAnalysisPort =
                new FakeTransactionAnalysisPort(
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
                                                "ORDINARY",
                                                "카드출금분"
                                        )
                                )
                        ),
                        true
                );

        DailyTransactionClassificationService service =
                new DailyTransactionClassificationService(
                        transactionAnalysisPort,
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
                transactionAnalysisPort.queryCalls
        );

        assertEquals(
                2,
                transactionAnalysisPort.saved.size()
        );
    }

    @Test
    void classifiesOnlyTheRequestedUsersUnanalyzedTransactions() {
        FakeTransactionAnalysisPort transactionAnalysisPort = new FakeTransactionAnalysisPort(
                List.of(target(1L, "ORDINARY", "스타벅스"))
        );
        DailyTransactionClassificationService service = new DailyTransactionClassificationService(
                transactionAnalysisPort, new FakeFastApiClient(), new TransactionPreClassificationService(),
                Runnable::run
        );

        assertEquals(1, service.classifyUnanalyzedTransactions(42L));
        assertEquals(List.of(42L, 42L), transactionAnalysisPort.queriedUserIds);
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

    private static class FakeTransactionAnalysisPort
            implements TransactionAnalysisPort {

        private final List<
                List<ClassificationTargetTransaction>
                > pages;

        private int pageIndex;
        private int queryCalls;
        private final List<Long> queriedUserIds = new ArrayList<>();

        private final List<TransactionAnalysisResult> saved =
                new ArrayList<>();

        private FakeTransactionAnalysisPort(
                List<ClassificationTargetTransaction> nextPage
        ) {
            this.pages = List.of(
                    List.copyOf(nextPage)
            );
        }

        private FakeTransactionAnalysisPort(
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
