package com.ntropy.work.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.VirtualSettlementDepositBatchResult;
import com.ntropy.work.domain.VirtualSettlementDepositBatchResult.MatchTarget;
import com.ntropy.work.service.SettlementService;
import com.ntropy.work.service.VirtualSettlementDepositService;

class SettlementSchedulerTest {

    @Test
    void immediatelyMatchesBackdatedVirtualDepositOutsideRegularBackfillWindow() {
        LocalDate today = LocalDate.now();
        LocalDate oldDepositDate = today.minusDays(30);
        StubVirtualDepositService virtualService = new StubVirtualDepositService(
                new VirtualSettlementDepositBatchResult(1, List.of(new MatchTarget(1L, oldDepositDate))));
        RecordingSettlementService settlementService = new RecordingSettlementService(oldDepositDate);
        SettlementScheduler scheduler = new SettlementScheduler(settlementService, virtualService);

        scheduler.runDailySettlementBatch();

        assertEquals(oldDepositDate, settlementService.processedDate);
        assertEquals(1L, settlementService.notifiedUserId);
        assertEquals(1, settlementService.notifiedCount);
        assertEquals(50_000L, settlementService.notifiedAmount);
        assertTrue(settlementService.dailyBatchCalled);
    }

    private static final class StubVirtualDepositService extends VirtualSettlementDepositService {
        private final VirtualSettlementDepositBatchResult result;

        private StubVirtualDepositService(VirtualSettlementDepositBatchResult result) {
            super(null, null, null, null, null, null);
            this.result = result;
        }

        @Override
        public VirtualSettlementDepositBatchResult runDailyBatch(LocalDate processDate) {
            return result;
        }
    }

    private static final class RecordingSettlementService extends SettlementService {
        private final LocalDate expectedDate;
        private LocalDate processedDate;
        private Long notifiedUserId;
        private int notifiedCount;
        private long notifiedAmount;
        private boolean dailyBatchCalled;

        private RecordingSettlementService(LocalDate expectedDate) {
            super(null, null, null, null, null, null, null, null, null, null, null);
            this.expectedDate = expectedDate;
        }

        @Override
        public SettlementBatchOutcome processSettlementDetailed(Long userId, LocalDate processDate) {
            processedDate = processDate;
            return expectedDate.equals(processDate)
                    ? new SettlementBatchOutcome(1, 50_000L)
                    : new SettlementBatchOutcome(0, 0L);
        }

        @Override
        public void notifySettlementCompleted(Long userId, LocalDate today, int count, long totalAmount) {
            notifiedUserId = userId;
            notifiedCount = count;
            notifiedAmount = totalAmount;
        }

        @Override
        public void runDailyBatch() {
            dailyBatchCalled = true;
        }
    }
}
