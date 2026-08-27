package com.ntropy.account.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.api.dto.DailyFinancialSyncResult.InstitutionSyncResult;
import com.ntropy.account.config.IncrementalSyncPolicy;
import com.ntropy.account.domain.AccountSyncStatus;
import com.ntropy.account.domain.Batching;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.IncrementalSyncRangeCalculator;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.exception.LeaseLostException;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.security.BirthDateCipher;
import com.ntropy.account.service.AccountCollectionService.AccountCollectionOutcome;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

import lombok.RequiredArgsConstructor;

/**
 * {@code provider=CODEF} 연결의 일일 증분 동기화를 조합한다 (이슈 #158).
 * 사용자·기관·계좌 단위로 실패를 격리하며, 기관 watermark(ACCOUNT_SYNC_STATE) 전진과 heartbeat는
 * {@link BatchExecutionLeaseService}가 부여한 lease의 fencing을 통과할 때만 이뤄진다.
 * lease를 잃으면(heartbeat 또는 watermark 갱신 실패) 남은 처리를 즉시 중단한다.
 */
@Service
@RequiredArgsConstructor
public class DailyCodefSyncService {

    public static final String JOB_NAME = "daily-sync-codef";

    /** 사용자 ID IN 절이 과도하게 커지지 않도록 나누는 chunk 크기 (이슈 #233). */
    private static final int USER_ID_CHUNK_SIZE = 500;

    private final CodefConnectionMapper codefConnectionMapper;
    private final AccountSyncStateMapper accountSyncStateMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final AccountCollectionService accountCollectionService;
    private final BatchExecutionLeaseService leaseService;
    private final BirthDateCipher birthDateCipher;
    private final IncrementalSyncPolicy incrementalSyncPolicy;

    public DailyFinancialSyncResult synchronize(List<Long> activeUserIds, LocalDate businessDate, LeaseHandle lease) {
        Set<Long> successfulUserIds = new LinkedHashSet<>();
        Set<Long> partialFailedUserIds = new LinkedHashSet<>();
        Map<Long, Set<YearMonth>> affectedYearMonthsByUser = new LinkedHashMap<>();
        List<InstitutionSyncResult> institutionResults = new ArrayList<>();
        long processedTransactionCount = 0;
        boolean leaseLost = false;

        Map<Long, CodefConnection> connectionsByUserId = fetchConnectionsByUserId(activeUserIds);

        userLoop:
        for (Long userId : activeUserIds) {
            CodefConnection connection = connectionsByUserId.get(userId);
            if (connection == null || connection.getConnectedId() == null || connection.getConnectedId().isBlank()) {
                continue; // 이 provider의 동기화 대상이 아닌 사용자
            }

            boolean userHasFailure = false;
            boolean userHasSuccess = false;

            for (String organizationCode : InstitutionKeys.parse(connection.getRegisteredInstitutionKeys())) {
                if (!leaseService.heartbeat(lease)) {
                    leaseLost = true;
                    break userLoop;
                }

                PersonalBank bank;
                try {
                    bank = PersonalBank.fromOrganizationCode(organizationCode);
                } catch (IllegalArgumentException unsupportedOrganization) {
                    continue; // 지원 대상 밖 기관코드 방어
                }

                // 실패도 ACCOUNT_SYNC_STATE에 남길 수 있도록 외부 호출 전에 상태 행을 보장한다.
                accountSyncStateMapper.insertIfAbsent(pendingSyncState(connection.getId(), organizationCode));

                InstitutionSyncOutcome outcome;
                try {
                    outcome = synchronizeInstitution(userId, bank, connection, businessDate, lease);
                } catch (LeaseLostException leaseLostSignal) {
                    leaseLost = true;
                    break userLoop;
                }

                processedTransactionCount += outcome.transactionCount();
                institutionResults.add(new InstitutionSyncResult(
                        organizationCode, connection.getId(), outcome.aggregate().statusName(),
                        outcome.aggregate().errorCode()
                ));

                if (outcome.aggregate().hasFailure()) {
                    userHasFailure = true;
                    boolean marked = accountSyncStateMapper.markStatusIfOwner(
                            connection.getId(), organizationCode, AccountSyncStatus.PARTIAL_FAILED.name(),
                            outcome.aggregate().errorCode(), lease.jobName(), lease.businessDate(),
                            lease.ownerId(), lease.leaseToken()
                    ) == 1;
                    if (!marked) {
                        leaseLost = true;
                        break userLoop;
                    }
                    continue; // watermark 전진하지 않음
                }

                boolean advanced = accountSyncStateMapper.advanceIfOwner(
                        connection.getId(), organizationCode, outcome.watermarkStatus(), null,
                        lease.jobName(), lease.businessDate(), lease.ownerId(), lease.leaseToken()
                ) == 1;
                if (!advanced) {
                    leaseLost = true;
                    break userLoop;
                }
                if (outcome.hadAnySuccess()) {
                    userHasSuccess = true;
                    if (!outcome.affectedYearMonths().isEmpty()) {
                        affectedYearMonthsByUser
                                .computeIfAbsent(userId, id -> new TreeSet<>())
                                .addAll(outcome.affectedYearMonths());
                    }
                }
            }

            if (userHasFailure) {
                partialFailedUserIds.add(userId);
            } else if (userHasSuccess) {
                successfulUserIds.add(userId);
            }
        }

        String executionStatus = leaseLost ? "FAILED"
                : partialFailedUserIds.isEmpty() ? "SUCCESS" : "PARTIAL_FAILED";

        return new DailyFinancialSyncResult(
                businessDate,
                DailyFinancialSyncProvider.CODEF,
                executionStatus,
                List.copyOf(successfulUserIds),
                affectedYearMonthsByUser.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()))),
                List.copyOf(partialFailedUserIds),
                List.copyOf(institutionResults),
                processedTransactionCount
        );
    }

    /** 활성 사용자의 CODEF 연결을 chunk 단위로 일괄 조회한다 (이슈 #233). */
    private Map<Long, CodefConnection> fetchConnectionsByUserId(List<Long> userIds) {
        Map<Long, CodefConnection> connectionsByUserId = new LinkedHashMap<>();
        for (List<Long> chunk : Batching.chunk(userIds, USER_ID_CHUNK_SIZE)) {
            for (CodefConnection connection : codefConnectionMapper.findByUserIdsAndProvider(chunk, ConnectionProvider.CODEF.name())) {
                connectionsByUserId.put(connection.getUserId(), connection);
            }
        }
        return connectionsByUserId;
    }

    private InstitutionSyncOutcome synchronizeInstitution(Long userId, PersonalBank bank, CodefConnection connection,
                                                           LocalDate businessDate, LeaseHandle lease) {
        String organizationCode = bank.getOrganizationCode();

        String birthDate = null;
        if (bank.isBirthDateRequired()) {
            if (connection.getBirthDateCiphertext() == null || connection.getBirthDateIv() == null) {
                return InstitutionSyncOutcome.failed(InstitutionAggregate.failed("CREDENTIAL_MISSING"));
            }
            try {
                birthDate = birthDateCipher.decrypt(connection.getBirthDateCiphertext(), connection.getBirthDateIv());
            } catch (RuntimeException decryptFailure) {
                // 손상된 IV·암호문이나 키 버전 불일치 하나가 전체 배치를 막으면 안 되므로 이 기관만 실패 처리한다.
                return InstitutionSyncOutcome.failed(InstitutionAggregate.failed("CREDENTIAL_DECRYPT_FAILED"));
            }
        }

        AccountSyncState state = accountSyncStateMapper.findByConnectionAndOrganization(connection.getId(), organizationCode);
        LocalDate mostRecentStoredDate =
                accountTransactionMapper.findMostRecentTransactionDate(connection.getId(), organizationCode);
        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                state == null ? null : state.getLastSuccessfulSyncedAt(),
                mostRecentStoredDate, businessDate, incrementalSyncPolicy
        );

        List<AccountCollectionOutcome> outcomes;
        try {
            outcomes = accountCollectionService.collectForDailySync(
                    userId, bank, connection, birthDate, startDate, businessDate, () -> leaseService.heartbeat(lease)
            );
        } catch (LeaseLostException leaseLostSignal) {
            throw leaseLostSignal; // 상위 루프에서 전체 중단 처리
        } catch (RuntimeException e) {
            return InstitutionSyncOutcome.failed(InstitutionAggregate.failed("COLLECTION_FAILED"));
        }

        boolean hasRealFailure = outcomes.stream()
                .anyMatch(o -> o.status() == AccountCollectionOutcome.Status.FAILED);
        boolean hasSuccess = outcomes.stream()
                .anyMatch(o -> o.status() == AccountCollectionOutcome.Status.SUCCESS);
        boolean hasSkipped = outcomes.stream()
                .anyMatch(o -> o.status() == AccountCollectionOutcome.Status.SKIPPED_CREDENTIAL_REQUIRED);
        long transactionCount = outcomes.stream().mapToLong(AccountCollectionOutcome::transactionCount).sum();
        Set<YearMonth> affectedYearMonths = outcomes.stream()
                .flatMap(outcome -> outcome.affectedYearMonths().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        if (hasRealFailure) {
            String errorCode = outcomes.stream()
                    .filter(outcome -> outcome.status() == AccountCollectionOutcome.Status.FAILED)
                    .map(AccountCollectionOutcome::errorCode)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse("COLLECTION_FAILED");
            return new InstitutionSyncOutcome(
                    InstitutionAggregate.failed(errorCode), false, affectedYearMonths, transactionCount, null
            );
        }
        // SKIPPED_CREDENTIAL_REQUIRED는 실패가 아니므로 watermark는 전진시키되(hasFailure=false),
        // 일부 계좌가 비밀번호 요구로 제외됐다는 사실 자체는 상태에 남긴다.
        InstitutionAggregate aggregate = hasSkipped ? InstitutionAggregate.skipped() : InstitutionAggregate.success();
        String watermarkStatus = hasSkipped
                ? AccountSyncStatus.SKIPPED_CREDENTIAL_REQUIRED.name()
                : AccountSyncStatus.SUCCESS.name();
        return new InstitutionSyncOutcome(aggregate, hasSuccess, affectedYearMonths, transactionCount, watermarkStatus);
    }

    private static AccountSyncState pendingSyncState(Long codefConnectionId, String organizationCode) {
        AccountSyncState state = new AccountSyncState();
        state.setCodefConnectionId(codefConnectionId);
        state.setOrganizationCode(organizationCode);
        state.setLastStatus(AccountSyncStatus.PENDING);
        return state;
    }

    private record InstitutionSyncOutcome(InstitutionAggregate aggregate, boolean hadAnySuccess,
                                          Set<YearMonth> affectedYearMonths, long transactionCount,
                                          String watermarkStatus) {
        static InstitutionSyncOutcome failed(InstitutionAggregate aggregate) {
            return new InstitutionSyncOutcome(aggregate, false, Set.of(), 0, null);
        }
    }

    private record InstitutionAggregate(Status status, String errorCode) {

        enum Status {
            SUCCESS,
            SKIPPED_CREDENTIAL_REQUIRED,
            PARTIAL_FAILED
        }

        static InstitutionAggregate success() {
            return new InstitutionAggregate(Status.SUCCESS, null);
        }

        static InstitutionAggregate skipped() {
            return new InstitutionAggregate(Status.SKIPPED_CREDENTIAL_REQUIRED, "SKIPPED_CREDENTIAL_REQUIRED");
        }

        static InstitutionAggregate failed(String errorCode) {
            return new InstitutionAggregate(Status.PARTIAL_FAILED, errorCode);
        }

        boolean hasFailure() {
            return status == Status.PARTIAL_FAILED;
        }

        boolean isSkipped() {
            return status == Status.SKIPPED_CREDENTIAL_REQUIRED;
        }

        String statusName() {
            return status.name();
        }
    }
}
