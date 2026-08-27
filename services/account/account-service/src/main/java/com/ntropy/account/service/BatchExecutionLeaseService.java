package com.ntropy.account.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ntropy.account.config.DailySyncLeaseProperties;
import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.domain.entity.DailyBatchExecution;
import com.ntropy.account.mapper.DailyBatchExecutionMapper;

import lombok.RequiredArgsConstructor;

/**
 * {@code DAILY_BATCH_EXECUTION} 기반 분산 lease를 관리한다 (이슈 #158).
 * 별도 heartbeat 스레드를 두지 않으며, 호출자가 사용자·기관 단위 처리 루프마다
 * {@link #heartbeat(LeaseHandle)}을 호출해 lease_until을 연장하는 것을 전제로 한다.
 * heartbeat·완료는 owner_id + lease_token 기반 fencing으로 보호되므로, lease를 이미 잃은
 * 호출자가 실행 상태를 덮어쓸 수 없다.
 *
 * <p>획득·heartbeat·완료 SQL이 DB {@code NOW()}로 만료 여부를 판정하고 새 시각도 같은 문장에서
 * 계산한다. 따라서 인스턴스별 시계 차이나 DB 시각을 조회한 뒤의 일시 중단에 영향받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BatchExecutionLeaseService {

    private final DailyBatchExecutionMapper dailyBatchExecutionMapper;
    private final DailySyncLeaseProperties leaseProperties;

    /**
     * lease 획득을 시도한다. INSERT가 UNIQUE(job_name, business_date) 위반으로 실패하면
     * 만료됐거나 완료된 기존 실행에 대해 조건부 UPDATE를 시도하고, 영향받은 row 수가 1이 아니면
     * 다른 인스턴스가 이미 선점한 것으로 보고 빈 Optional을 반환한다.
     */
    public Optional<LeaseHandle> acquire(String jobName, LocalDate businessDate, String ownerId) {
        String leaseToken = UUID.randomUUID().toString();
        long leaseDurationSeconds = leaseProperties.getLeaseDuration().toSeconds();

        DailyBatchExecution execution = new DailyBatchExecution();
        execution.setJobName(jobName);
        execution.setBusinessDate(businessDate);
        execution.setStatus(BatchExecutionStatus.RUNNING);
        execution.setOwnerId(ownerId);
        execution.setLeaseToken(leaseToken);

        try {
            dailyBatchExecutionMapper.insert(execution, leaseDurationSeconds);
            return Optional.of(new LeaseHandle(execution.getId(), jobName, businessDate, ownerId, leaseToken));
        } catch (DuplicateKeyException alreadyExists) {
            int acquired = dailyBatchExecutionMapper.acquireExpiredLease(execution, leaseDurationSeconds);
            if (acquired != 1) {
                return Optional.empty();
            }
            DailyBatchExecution takenOver =
                    dailyBatchExecutionMapper.findByJobNameAndBusinessDate(jobName, businessDate);
            return Optional.of(new LeaseHandle(takenOver.getId(), jobName, businessDate, ownerId, leaseToken));
        }
    }

    /**
     * heartbeat. 영향받은 row 수가 0이면 소유권을 잃은 것이므로 {@code false}를 반환한다.
     * 호출자는 {@code false}를 받으면 watermark 갱신을 포함한 남은 처리를 즉시 중단해야 한다.
     */
    public boolean heartbeat(LeaseHandle lease) {
        int renewed = dailyBatchExecutionMapper.renewLease(
                lease.executionId(), lease.ownerId(), lease.leaseToken(), leaseProperties.getLeaseDuration().toSeconds()
        );
        return renewed == 1;
    }

    /**
     * 완료 처리(SUCCESS/PARTIAL_FAILED/FAILED). heartbeat와 동일한 fencing 조건을 쓰므로,
     * 이미 소유권을 잃은 뒤 호출하면 {@code false}가 반환되고 실행 상태는 바뀌지 않는다.
     */
    public boolean complete(LeaseHandle lease, BatchExecutionStatus status, String errorSummaryJson) {
        int completed = dailyBatchExecutionMapper.completeIfOwner(
                lease.executionId(), lease.ownerId(), lease.leaseToken(),
                status.name(), errorSummaryJson
        );
        return completed == 1;
    }

    /** 획득한 lease를 식별하는 값 객체. 이후 heartbeat/complete 호출에 그대로 넘긴다. */
    public record LeaseHandle(Long executionId, String jobName, LocalDate businessDate,
                               String ownerId, String leaseToken) {
    }
}
