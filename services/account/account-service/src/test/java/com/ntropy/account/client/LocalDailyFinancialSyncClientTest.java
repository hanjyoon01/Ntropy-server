package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.api.dto.DailyFinancialSyncResult.InstitutionSyncResult;
import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.service.BatchExecutionLeaseService;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.account.service.DailyCodefSyncService;
import com.ntropy.account.service.DailyNtropySyncService;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

class LocalDailyFinancialSyncClientTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 14);

    @Test
    void delegatesToCodefSyncServiceAndCompletesLeaseOnSuccess() {
        StubLeaseService leaseService = new StubLeaseService(true);
        StubCodefSyncService codefSync = new StubCodefSyncService(successResult(DailyFinancialSyncProvider.CODEF));
        StubNtropySyncService ntropySync = new StubNtropySyncService(null);
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(leaseService, codefSync, ntropySync);

        DailyFinancialSyncResult result = client.synchronize(DailyFinancialSyncProvider.CODEF, List.of(1L), BUSINESS_DATE);

        assertEquals("SUCCESS", result.executionStatus());
        assertTrue(codefSync.called);
        assertFalse(ntropySync.called);
        assertEquals(DailyCodefSyncService.JOB_NAME, leaseService.lastAcquireJobName);
        assertEquals(BatchExecutionStatus.SUCCESS, leaseService.lastCompleteStatus);
        assertNull(leaseService.lastCompleteErrorSummary);
    }

    @Test
    void delegatesToNtropySyncServiceForNtropyProvider() {
        StubLeaseService leaseService = new StubLeaseService(true);
        StubCodefSyncService codefSync = new StubCodefSyncService(null);
        StubNtropySyncService ntropySync = new StubNtropySyncService(successResult(DailyFinancialSyncProvider.NTROPY));
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(leaseService, codefSync, ntropySync);

        DailyFinancialSyncResult result = client.synchronize(DailyFinancialSyncProvider.NTROPY, List.of(1L), BUSINESS_DATE);

        assertEquals("SUCCESS", result.executionStatus());
        assertTrue(ntropySync.called);
        assertFalse(codefSync.called);
        assertEquals(DailyNtropySyncService.JOB_NAME, leaseService.lastAcquireJobName);
    }

    @Test
    void returnsSkippedResultWithoutSynchronizingWhenLeaseIsAlreadyHeld() {
        StubLeaseService leaseService = new StubLeaseService(false);
        StubCodefSyncService codefSync = new StubCodefSyncService(null);
        StubNtropySyncService ntropySync = new StubNtropySyncService(null);
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(leaseService, codefSync, ntropySync);

        DailyFinancialSyncResult result = client.synchronize(DailyFinancialSyncProvider.CODEF, List.of(1L), BUSINESS_DATE);

        assertEquals("SKIPPED", result.executionStatus());
        assertFalse(codefSync.called);
        assertTrue(result.successfulUserIds().isEmpty());
        assertFalse(leaseService.completeCalled);
    }

    @Test
    void buildsErrorSummaryWithoutSensitiveFieldsWhenPartialFailed() {
        StubLeaseService leaseService = new StubLeaseService(true);
        DailyFinancialSyncResult partialFailed = new DailyFinancialSyncResult(
                BUSINESS_DATE, DailyFinancialSyncProvider.CODEF, "PARTIAL_FAILED",
                List.of(), Map.of(), List.of(2L),
                List.of(new InstitutionSyncResult("0004", 22L, "PARTIAL_FAILED", "TIMEOUT")),
                3
        );
        StubCodefSyncService codefSync = new StubCodefSyncService(partialFailed);
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(
                leaseService, codefSync, new StubNtropySyncService(null)
        );

        client.synchronize(DailyFinancialSyncProvider.CODEF, List.of(2L), BUSINESS_DATE);

        assertEquals(BatchExecutionStatus.PARTIAL_FAILED, leaseService.lastCompleteStatus);
        assertTrue(leaseService.lastCompleteErrorSummary.contains("0004"));
        assertTrue(leaseService.lastCompleteErrorSummary.contains("TIMEOUT"));
        assertTrue(leaseService.lastCompleteErrorSummary.contains("\"codefConnectionId\":22"));
        assertFalse(leaseService.lastCompleteErrorSummary.contains("affectedUserIds"));
        assertFalse(leaseService.lastCompleteErrorSummary.contains("connectedId"));
    }

    @Test
    void downgradesToFailedWhenCompleteLosesOwnershipAfterSuccessfulSync() {
        // 마지막 heartbeat 이후 completeIfOwner 사이에 lease를 잃은 경우를 흉내낸다. 내부적으로는
        // SUCCESS를 계산했더라도, 소유권을 확인하지 못한 실행을 호출자에게 SUCCESS로 보고하면 안 된다.
        StubLeaseService leaseService = new StubLeaseService(true, false);
        StubCodefSyncService codefSync = new StubCodefSyncService(successResult(DailyFinancialSyncProvider.CODEF));
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(
                leaseService, codefSync, new StubNtropySyncService(null)
        );

        DailyFinancialSyncResult result = client.synchronize(DailyFinancialSyncProvider.CODEF, List.of(1L), BUSINESS_DATE);

        assertEquals("FAILED", result.executionStatus());
        assertTrue(leaseService.completeCalled);
        assertTrue(result.successfulUserIds().isEmpty());
        assertTrue(result.affectedYearMonthsByUser().isEmpty());
    }

    @Test
    void completesAsFailedAndReturnsEmptyPayloadWhenSyncThrowsUnexpectedly() {
        StubLeaseService leaseService = new StubLeaseService(true);
        StubCodefSyncService codefSync = new StubCodefSyncService(
                null, new IllegalStateException("sensitive-message")
        );
        LocalDailyFinancialSyncClient client = new LocalDailyFinancialSyncClient(
                leaseService, codefSync, new StubNtropySyncService(null)
        );

        DailyFinancialSyncResult result = client.synchronize(
                DailyFinancialSyncProvider.CODEF, List.of(1L), BUSINESS_DATE
        );

        assertEquals("FAILED", result.executionStatus());
        assertTrue(result.successfulUserIds().isEmpty());
        assertEquals(BatchExecutionStatus.FAILED, leaseService.lastCompleteStatus);
        assertTrue(leaseService.lastCompleteErrorSummary.contains("UNEXPECTED_SYNC_FAILURE"));
        assertFalse(leaseService.lastCompleteErrorSummary.contains("sensitive-message"));
    }

    private static DailyFinancialSyncResult successResult(DailyFinancialSyncProvider provider) {
        return new DailyFinancialSyncResult(
                BUSINESS_DATE, provider, "SUCCESS", List.of(1L),
                Map.of(1L, List.of(YearMonth.of(2026, 8))), List.of(), List.of(), 5
        );
    }

    private static class StubLeaseService extends BatchExecutionLeaseService {

        private final boolean acquireSucceeds;
        private final boolean completeSucceeds;
        private String lastAcquireJobName;
        private boolean completeCalled;
        private BatchExecutionStatus lastCompleteStatus;
        private String lastCompleteErrorSummary;

        StubLeaseService(boolean acquireSucceeds) {
            this(acquireSucceeds, true);
        }

        StubLeaseService(boolean acquireSucceeds, boolean completeSucceeds) {
            super(null, null);
            this.acquireSucceeds = acquireSucceeds;
            this.completeSucceeds = completeSucceeds;
        }

        @Override
        public Optional<LeaseHandle> acquire(String jobName, LocalDate businessDate, String ownerId) {
            lastAcquireJobName = jobName;
            if (!acquireSucceeds) {
                return Optional.empty();
            }
            return Optional.of(new LeaseHandle(1L, jobName, businessDate, ownerId, "token"));
        }

        @Override
        public boolean complete(LeaseHandle lease, BatchExecutionStatus status, String errorSummaryJson) {
            completeCalled = true;
            lastCompleteStatus = status;
            lastCompleteErrorSummary = errorSummaryJson;
            return completeSucceeds;
        }
    }

    private static class StubCodefSyncService extends DailyCodefSyncService {

        private final DailyFinancialSyncResult result;
        private final RuntimeException failure;
        private boolean called;

        StubCodefSyncService(DailyFinancialSyncResult result) {
            super(null, null, null, null, null, null, null);
            this.result = result;
            this.failure = null;
        }

        StubCodefSyncService(DailyFinancialSyncResult result, RuntimeException failure) {
            super(null, null, null, null, null, null, null);
            this.result = result;
            this.failure = failure;
        }

        @Override
        public DailyFinancialSyncResult synchronize(List<Long> activeUserIds, LocalDate businessDate, LeaseHandle lease) {
            called = true;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static class StubNtropySyncService extends DailyNtropySyncService {

        private final DailyFinancialSyncResult result;
        private boolean called;

        StubNtropySyncService(DailyFinancialSyncResult result) {
            super(null, null, null, null, null, null, null);
            this.result = result;
        }

        @Override
        public DailyFinancialSyncResult synchronize(List<Long> activeUserIds, LocalDate businessDate, LeaseHandle lease) {
            called = true;
            return result;
        }
    }
}
