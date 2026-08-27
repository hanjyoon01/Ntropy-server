package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.ntropy.account.config.DailySyncLeaseProperties;
import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.domain.entity.DailyBatchExecution;
import com.ntropy.account.mapper.DailyBatchExecutionMapper;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;

/**
 * lease 획득/heartbeat/완료의 row-count 기반 fencing이 이슈 #158에서 합의한 규칙대로
 * 동작하는지 검증한다. 실제 SQL 동시성은 {@code DailyBatchExecutionLeaseManualVerificationTest}가
 * 실제 MySQL로 별도 검증한다.
 */
class BatchExecutionLeaseServiceTest {

    private static final String JOB_NAME = "daily-sync-codef";
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 14);

    @Test
    void acquireSucceedsForNewExecution() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);

        Optional<LeaseHandle> lease = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a");

        assertTrue(lease.isPresent());
        assertEquals("owner-a", lease.get().ownerId());
    }

    @Test
    void acquireFailsWhileAnotherOwnerHoldsValidLease() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);

        service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a");
        Optional<LeaseHandle> second = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-b");

        assertTrue(second.isEmpty());
    }

    @Test
    void acquireSucceedsAfterLeaseExpiresAndIssuesNewLeaseToken() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);

        LeaseHandle first = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a").orElseThrow();
        clock.advanceBy(Duration.ofSeconds(400)); // 기본 lease 기간(360초)보다 길게 경과

        LeaseHandle second = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-b").orElseThrow();

        assertEquals("owner-b", second.ownerId());
        assertNotEquals(first.leaseToken(), second.leaseToken());
    }

    @Test
    void heartbeatSucceedsWhileOwning() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);
        LeaseHandle lease = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a").orElseThrow();

        clock.advanceBy(Duration.ofSeconds(100));

        assertTrue(service.heartbeat(lease));
    }

    @Test
    void heartbeatFailsAfterAnotherInstanceTakesOverExpiredLease() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);
        LeaseHandle staleLease = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a").orElseThrow();

        clock.advanceBy(Duration.ofSeconds(400));
        service.acquire(JOB_NAME, BUSINESS_DATE, "owner-b").orElseThrow();

        // owner-a는 lease를 잃은 줄 모른 채 옛 handle로 heartbeat를 시도한다.
        assertFalse(service.heartbeat(staleLease));
    }

    @Test
    void completeFailsAfterAnotherInstanceTakesOverExpiredLease() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);
        LeaseHandle staleLease = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a").orElseThrow();

        clock.advanceBy(Duration.ofSeconds(400));
        service.acquire(JOB_NAME, BUSINESS_DATE, "owner-b").orElseThrow();

        // owner-a가 뒤늦게 살아나 완료 처리를 시도해도 owner-b의 실행을 덮어쓰지 못한다.
        assertFalse(service.complete(staleLease, BatchExecutionStatus.SUCCESS, null));
    }

    @Test
    void completeSucceedsWhileOwning() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);
        LeaseHandle lease = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a").orElseThrow();

        assertTrue(service.complete(lease, BatchExecutionStatus.SUCCESS, null));
    }

    @Test
    void acquireSucceedsAgainAfterSuccessfulCompletionOnSameBusinessDate() {
        MutableClock clock = MutableClock.at(2026, 8, 14, 1, 0);
        BatchExecutionLeaseService service = newService(clock);
        LeaseHandle first = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a").orElseThrow();
        service.complete(first, BatchExecutionStatus.SUCCESS, null);

        // status != 'RUNNING' 조건은 동시 실행만 막을 뿐, 완료된 실행의 재호출(수동 재시도)은 막지 않는다.
        Optional<LeaseHandle> rerun = service.acquire(JOB_NAME, BUSINESS_DATE, "owner-a");

        assertTrue(rerun.isPresent());
        assertNotEquals(first.leaseToken(), rerun.get().leaseToken());
    }

    private static BatchExecutionLeaseService newService(MutableClock clock) {
        DailySyncLeaseProperties properties = new DailySyncLeaseProperties(360);
        return new BatchExecutionLeaseService(new InMemoryDailyBatchExecutionMapper(clock), properties);
    }

    /** 테스트에서 시간을 직접 흘려보낼 수 있는 {@link Clock}. */
    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        static MutableClock at(int year, int month, int day, int hour, int minute) {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            Instant instant = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant();
            return new MutableClock(instant, zone);
        }

        void advanceBy(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /**
     * MySQL의 UNIQUE(job_name, business_date) + 조건부 UPDATE + fencing WHERE 절 의미론을
     * 메모리에서 흉내내는 fake. 실제 SQL 동시성 자체는 여기서 검증하지 않는다.
     */
    private static final class InMemoryDailyBatchExecutionMapper implements DailyBatchExecutionMapper {

        private final Map<String, DailyBatchExecution> store = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);
        private final Clock clock;

        private InMemoryDailyBatchExecutionMapper(Clock clock) {
            this.clock = clock;
        }

        @Override
        public void insert(DailyBatchExecution execution, long leaseDurationSeconds) {
            String key = key(execution.getJobName(), execution.getBusinessDate());
            if (store.containsKey(key)) {
                throw new DuplicateKeyException("uk_daily_batch_execution_job_date 위반: " + key);
            }
            execution.setId(idSequence.getAndIncrement());
            execution.setLeaseUntil(LocalDateTime.now(clock).plusSeconds(leaseDurationSeconds));
            execution.setStartedAt(LocalDateTime.now(clock));
            store.put(key, copy(execution));
        }

        @Override
        public int acquireExpiredLease(DailyBatchExecution execution, long leaseDurationSeconds) {
            LocalDateTime now = LocalDateTime.now(clock);
            String key = key(execution.getJobName(), execution.getBusinessDate());
            DailyBatchExecution existing = store.get(key);
            if (existing == null) {
                return 0;
            }
            boolean expiredOrNotRunning = existing.getStatus() != BatchExecutionStatus.RUNNING
                    || existing.getLeaseUntil().isBefore(now);
            if (!expiredOrNotRunning) {
                return 0;
            }
            existing.setStatus(execution.getStatus());
            existing.setOwnerId(execution.getOwnerId());
            existing.setLeaseToken(execution.getLeaseToken());
            existing.setLeaseUntil(now.plusSeconds(leaseDurationSeconds));
            existing.setStartedAt(now);
            existing.setCompletedAt(null);
            existing.setErrorSummary(null);
            return 1;
        }

        @Override
        public int renewLease(Long id, String ownerId, String leaseToken, long leaseDurationSeconds) {
            LocalDateTime now = LocalDateTime.now(clock);
            DailyBatchExecution existing = findById(id);
            if (existing == null || !ownsValidLease(existing, ownerId, leaseToken, now)) {
                return 0;
            }
            existing.setLeaseUntil(now.plusSeconds(leaseDurationSeconds));
            return 1;
        }

        @Override
        public int completeIfOwner(Long id, String ownerId, String leaseToken, String status,
                                    String errorSummary) {
            LocalDateTime now = LocalDateTime.now(clock);
            DailyBatchExecution existing = findById(id);
            if (existing == null || !ownsValidLease(existing, ownerId, leaseToken, now)) {
                return 0;
            }
            existing.setStatus(BatchExecutionStatus.valueOf(status));
            existing.setCompletedAt(now);
            existing.setErrorSummary(errorSummary);
            return 1;
        }

        @Override
        public DailyBatchExecution findByJobNameAndBusinessDate(String jobName, LocalDate businessDate) {
            DailyBatchExecution existing = store.get(key(jobName, businessDate));
            return existing == null ? null : copy(existing);
        }

        private boolean ownsValidLease(DailyBatchExecution existing, String ownerId, String leaseToken,
                                        LocalDateTime now) {
            return existing.getStatus() == BatchExecutionStatus.RUNNING
                    && existing.getOwnerId().equals(ownerId)
                    && existing.getLeaseToken().equals(leaseToken)
                    && !existing.getLeaseUntil().isBefore(now);
        }

        private DailyBatchExecution findById(Long id) {
            return store.values().stream().filter(e -> e.getId().equals(id)).findFirst().orElse(null);
        }

        private static String key(String jobName, LocalDate businessDate) {
            return jobName + "|" + businessDate;
        }

        private static DailyBatchExecution copy(DailyBatchExecution source) {
            DailyBatchExecution copy = new DailyBatchExecution();
            copy.setId(source.getId());
            copy.setJobName(source.getJobName());
            copy.setBusinessDate(source.getBusinessDate());
            copy.setStatus(source.getStatus());
            copy.setOwnerId(source.getOwnerId());
            copy.setLeaseToken(source.getLeaseToken());
            copy.setLeaseUntil(source.getLeaseUntil());
            copy.setStartedAt(source.getStartedAt());
            copy.setCompletedAt(source.getCompletedAt());
            copy.setErrorSummary(source.getErrorSummary());
            return copy;
        }
    }
}
