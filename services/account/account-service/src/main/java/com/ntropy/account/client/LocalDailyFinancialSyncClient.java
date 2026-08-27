package com.ntropy.account.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.api.client.DailyFinancialSyncClient;
import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.domain.InstanceOwnerId;
import com.ntropy.account.exception.LeaseLostException;
import com.ntropy.account.service.BatchExecutionLeaseService;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.account.service.DailyCodefSyncService;
import com.ntropy.account.service.DailyNtropySyncService;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

import lombok.RequiredArgsConstructor;

/**
 * {@link DailyFinancialSyncClient}의 account-service 구현체 (이슈 #158).
 * provider별 job_name으로 lease를 획득하고, 성공적으로 획득했을 때만 실제 동기화를 실행한다.
 * 이미 다른 인스턴스가 유효한 lease를 쥐고 있으면(같은 job_name/business_date 동시 실행) 아무 것도
 * 하지 않고 executionStatus="SKIPPED"를 반환한다. 동기화가 끝나면 그 결과로 lease를 완료 처리한다.
 */
@Component
@RequiredArgsConstructor
public class LocalDailyFinancialSyncClient implements DailyFinancialSyncClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BatchExecutionLeaseService leaseService;
    private final DailyCodefSyncService codefSyncService;
    private final DailyNtropySyncService ntropySyncService;

    private final String ownerId = InstanceOwnerId.generate();

    @Override
    public DailyFinancialSyncResult synchronize(DailyFinancialSyncProvider provider, List<Long> activeUserIds,
                                                LocalDate businessDate) {
        String jobName = jobNameFor(provider);
        Optional<LeaseHandle> lease = leaseService.acquire(jobName, businessDate, ownerId);
        if (lease.isEmpty()) {
            return skippedResult(provider, businessDate);
        }

        LeaseHandle acquiredLease = lease.get();
        try {
            DailyFinancialSyncResult result = executeSync(provider, activeUserIds, businessDate, acquiredLease);
            boolean completed = leaseService.complete(
                    acquiredLease, toBatchExecutionStatus(result.executionStatus()), errorSummary(result)
            );
            if (!completed) {
                // 완료 fencing이 실패한 결과는 downstream이 소비할 수 없도록 성공 payload를 제거한다.
                return failedResult(result);
            }
            return result;
        } catch (LeaseLostException leaseLost) {
            // lease를 잃은 실행은 현재 소유자가 완료 상태를 기록해서는 안 된다.
            return failedResult(provider, businessDate);
        } catch (RuntimeException unexpectedFailure) {
            // 예상하지 못한 예외도 TTL 동안 RUNNING으로 남기지 않는다. 예외 메시지는 저장하지 않는다.
            try {
                leaseService.complete(
                        acquiredLease, BatchExecutionStatus.FAILED, unexpectedErrorSummary(provider)
                );
            } catch (RuntimeException completionFailure) {
                // DB 장애 등으로 완료 기록까지 실패해도 원래 예외나 민감한 메시지를 외부 결과에 노출하지 않는다.
            }
            return failedResult(provider, businessDate);
        }
    }

    private DailyFinancialSyncResult executeSync(DailyFinancialSyncProvider provider, List<Long> activeUserIds,
                                                 LocalDate businessDate, LeaseHandle lease) {
        return switch (provider) {
            case CODEF -> codefSyncService.synchronize(activeUserIds, businessDate, lease);
            case NTROPY -> ntropySyncService.synchronize(activeUserIds, businessDate, lease);
        };
    }

    private static String jobNameFor(DailyFinancialSyncProvider provider) {
        return switch (provider) {
            case CODEF -> DailyCodefSyncService.JOB_NAME;
            case NTROPY -> DailyNtropySyncService.JOB_NAME;
        };
    }

    private static BatchExecutionStatus toBatchExecutionStatus(String executionStatus) {
        return switch (executionStatus) {
            case "SUCCESS" -> BatchExecutionStatus.SUCCESS;
            case "PARTIAL_FAILED" -> BatchExecutionStatus.PARTIAL_FAILED;
            default -> BatchExecutionStatus.FAILED;
        };
    }

    /**
     * provider + organizationCode + 내부 codefConnectionId + errorCode만 담는다.
     * connectedId·사용자 ID·계좌번호·생년월일 등 외부 식별자나 민감정보는 포함하지 않는다.
     */
    private static String errorSummary(DailyFinancialSyncResult result) {
        List<Map<String, Object>> failures = result.institutionResults().stream()
                .filter(institution -> "PARTIAL_FAILED".equals(institution.status()))
                .map(institution -> Map.<String, Object>of(
                        "provider", result.provider().name(),
                        "organizationCode", institution.organizationCode(),
                        "codefConnectionId", institution.codefConnectionId(),
                        "errorCode", institution.errorCode() == null ? "" : institution.errorCode()
                ))
                .toList();
        if (failures.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("failures", failures));
        } catch (Exception e) {
            throw new IllegalStateException("오류 요약 직렬화 실패", e);
        }
    }

    private static DailyFinancialSyncResult skippedResult(DailyFinancialSyncProvider provider, LocalDate businessDate) {
        return new DailyFinancialSyncResult(
                businessDate, provider, "SKIPPED", List.of(), Map.of(), List.of(), List.of(), 0
        );
    }

    private static String unexpectedErrorSummary(DailyFinancialSyncProvider provider) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "failures", List.of(Map.of(
                            "provider", provider.name(),
                            "errorCode", "UNEXPECTED_SYNC_FAILURE"
                    ))
            ));
        } catch (Exception serializationFailure) {
            return "{\"failures\":[{\"errorCode\":\"UNEXPECTED_SYNC_FAILURE\"}]}";
        }
    }

    private static DailyFinancialSyncResult failedResult(DailyFinancialSyncResult result) {
        return new DailyFinancialSyncResult(
                result.businessDate(), result.provider(), "FAILED", List.of(), Map.of(),
                result.partialFailedUserIds(), result.institutionResults(),
                result.processedTransactionCount()
        );
    }

    private static DailyFinancialSyncResult failedResult(DailyFinancialSyncProvider provider, LocalDate businessDate) {
        return new DailyFinancialSyncResult(
                businessDate, provider, "FAILED", List.of(), Map.of(), List.of(), List.of(), 0
        );
    }
}
