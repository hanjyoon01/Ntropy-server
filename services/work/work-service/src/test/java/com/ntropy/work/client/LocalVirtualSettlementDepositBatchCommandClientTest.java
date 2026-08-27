package com.ntropy.work.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.work.api.client.VirtualSettlementDepositBatchCommandClient.BatchResult;
import com.ntropy.work.domain.VirtualSettlementDepositBatchResult;
import com.ntropy.work.domain.VirtualSettlementDepositBatchResult.MatchTarget;
import com.ntropy.work.service.SettlementService;
import com.ntropy.work.service.VirtualSettlementDepositService;

class LocalVirtualSettlementDepositBatchCommandClientTest {

    @Test
    void createsVirtualDepositsAndImmediatelyMatchesEveryTargetDate() {
        Long userId = 42L;
        LocalDate processDate = LocalDate.of(2026, 8, 23);
        LocalDate firstDepositDate = processDate.minusDays(30);
        LocalDate secondDepositDate = processDate.minusDays(1);
        StubVirtualDepositService depositService = new StubVirtualDepositService(
                new VirtualSettlementDepositBatchResult(2, List.of(
                        new MatchTarget(userId, firstDepositDate),
                        new MatchTarget(userId, secondDepositDate)
                )));
        RecordingSettlementService settlementService = new RecordingSettlementService(
                List.of(
                        new SettlementService.SettlementBatchOutcome(1, 40_000L),
                        new SettlementService.SettlementBatchOutcome(2, 70_000L)
                ));
        LocalVirtualSettlementDepositBatchCommandClient client =
                new LocalVirtualSettlementDepositBatchCommandClient(depositService, settlementService);

        BatchResult result = client.runForDate(userId, processDate);

        assertEquals(2, result.createdDepositCount());
        assertEquals(3, result.matchedSettlementCount());
        assertEquals(userId, depositService.processedUserId);
        assertEquals(processDate, depositService.processedDate);
        assertEquals(List.of(firstDepositDate, secondDepositDate), settlementService.processedDates);
        assertEquals(userId, settlementService.notifiedUserId);
        assertEquals(processDate, settlementService.notifiedDate);
        assertEquals(3, settlementService.notifiedCount);
        assertEquals(110_000L, settlementService.notifiedAmount);
    }

    @Test
    void returnsOnlyCreatedCountWhenThereAreNoMatchTargets() {
        Long userId = 42L;
        LocalDate processDate = LocalDate.of(2026, 8, 23);
        StubVirtualDepositService depositService = new StubVirtualDepositService(
                new VirtualSettlementDepositBatchResult(1, List.of()));
        RecordingSettlementService settlementService = new RecordingSettlementService(List.of());
        LocalVirtualSettlementDepositBatchCommandClient client =
                new LocalVirtualSettlementDepositBatchCommandClient(depositService, settlementService);

        BatchResult result = client.runForDate(userId, processDate);

        assertEquals(1, result.createdDepositCount());
        assertEquals(0, result.matchedSettlementCount());
        assertEquals(List.of(), settlementService.processedDates);
        assertNull(settlementService.notifiedUserId);
    }

    @Test
    void isolatesOneTargetsMatchingFailureFromTheOthers() {
        Long userId = 42L;
        LocalDate processDate = LocalDate.of(2026, 8, 23);
        LocalDate failingDepositDate = processDate.minusDays(30);
        LocalDate succeedingDepositDate = processDate.minusDays(1);
        StubVirtualDepositService depositService = new StubVirtualDepositService(
                new VirtualSettlementDepositBatchResult(2, List.of(
                        new MatchTarget(userId, failingDepositDate),
                        new MatchTarget(userId, succeedingDepositDate)
                )));
        RecordingSettlementService settlementService = new RecordingSettlementService(
                List.of(new SettlementService.SettlementBatchOutcome(1, 40_000L)));
        settlementService.failOnDate = failingDepositDate;
        LocalVirtualSettlementDepositBatchCommandClient client =
                new LocalVirtualSettlementDepositBatchCommandClient(depositService, settlementService);

        BatchResult result = client.runForDate(userId, processDate);

        assertEquals(2, result.createdDepositCount());
        assertEquals(1, result.matchedSettlementCount());
        assertEquals(List.of(failingDepositDate, succeedingDepositDate), settlementService.processedDates);
        assertEquals(1, settlementService.notifiedCount);
        assertEquals(40_000L, settlementService.notifiedAmount);
    }

    @Test
    void matchesAvailableTargetEvenWhenDepositWasAlreadyCreated() {
        Long userId = 42L;
        LocalDate processDate = LocalDate.of(2026, 8, 23);
        LocalDate depositDate = processDate.minusDays(7);
        StubVirtualDepositService depositService = new StubVirtualDepositService(
                new VirtualSettlementDepositBatchResult(
                        0, List.of(new MatchTarget(userId, depositDate))));
        RecordingSettlementService settlementService = new RecordingSettlementService(
                List.of(new SettlementService.SettlementBatchOutcome(1, 50_000L)));
        LocalVirtualSettlementDepositBatchCommandClient client =
                new LocalVirtualSettlementDepositBatchCommandClient(depositService, settlementService);

        BatchResult result = client.runForDate(userId, processDate);

        assertEquals(0, result.createdDepositCount());
        assertEquals(1, result.matchedSettlementCount());
        assertEquals(List.of(depositDate), settlementService.processedDates);
        assertEquals(1, settlementService.notifiedCount);
    }

    private static final class StubVirtualDepositService extends VirtualSettlementDepositService {
        private final VirtualSettlementDepositBatchResult result;
        private Long processedUserId;
        private LocalDate processedDate;

        private StubVirtualDepositService(VirtualSettlementDepositBatchResult result) {
            super(null, null, null, null, null, null);
            this.result = result;
        }

        @Override
        public VirtualSettlementDepositBatchResult processUser(Long userId, LocalDate processDate) {
            processedUserId = userId;
            processedDate = processDate;
            return result;
        }
    }

    private static final class RecordingSettlementService extends SettlementService {
        private final List<SettlementBatchOutcome> outcomes;
        private final List<LocalDate> processedDates = new ArrayList<>();
        private int outcomeIndex;
        private Long notifiedUserId;
        private LocalDate notifiedDate;
        private int notifiedCount;
        private long notifiedAmount;
        private LocalDate failOnDate;

        private RecordingSettlementService(List<SettlementBatchOutcome> outcomes) {
            super(null, null, null, null, null, null, null, null, null, null, null);
            this.outcomes = outcomes;
        }

        @Override
        public SettlementBatchOutcome processSettlementDetailed(Long userId, LocalDate processDate) {
            processedDates.add(processDate);
            if (processDate.equals(failOnDate)) {
                throw new IllegalStateException("정산 매칭 실패 시뮬레이션");
            }
            return outcomes.get(outcomeIndex++);
        }

        @Override
        public void notifySettlementCompleted(Long userId, LocalDate today, int count, long totalAmount) {
            notifiedUserId = userId;
            notifiedDate = today;
            notifiedCount = count;
            notifiedAmount = totalAmount;
        }
    }
}
