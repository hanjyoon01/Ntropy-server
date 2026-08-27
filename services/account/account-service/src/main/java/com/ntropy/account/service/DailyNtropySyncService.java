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
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountSyncStatus;
import com.ntropy.account.domain.Batching;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.IncrementalSyncRangeCalculator;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

import lombok.RequiredArgsConstructor;

/**
 * {@code provider=NTROPY} 연결의 일일 증분 가상 거래 생성을 조합한다 (이슈 #158).
 * 외부 호출이 없어 CODEF 경로보다 훨씬 빠르지만, {@code DAILY_BATCH_EXECUTION}의 별도
 * job_name({@value #JOB_NAME})으로 독립 실행하고 heartbeat·watermark fencing 규칙은 동일하게 따른다.
 */
@Service
@RequiredArgsConstructor
public class DailyNtropySyncService {

    public static final String JOB_NAME = "daily-sync-ntropy";
    private static final String ORDINARY_DEPOSIT_TYPE_CODE = "11";

    /** 사용자 ID IN 절이 과도하게 커지지 않도록 나누는 chunk 크기 (이슈 #233). */
    private static final int USER_ID_CHUNK_SIZE = 500;

    private final CodefConnectionMapper codefConnectionMapper;
    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final AccountSyncStateMapper accountSyncStateMapper;
    private final BatchExecutionLeaseService leaseService;
    private final NtropyIncrementalTransactionGenerator transactionGenerator;
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
            Map<String, Account> ordinaryAccountsByOrganization = findOrdinaryAccountsByOrganization(userId);

            for (String organizationCode : InstitutionKeys.parse(connection.getRegisteredInstitutionKeys())) {
                if (!leaseService.heartbeat(lease)) {
                    leaseLost = true;
                    break userLoop;
                }

                accountSyncStateMapper.insertIfAbsent(pendingSyncState(connection.getId(), organizationCode));
                InstitutionGenerationOutcome outcome = generateForInstitution(
                        connection, organizationCode, businessDate,
                        ordinaryAccountsByOrganization.get(organizationCode)
                );
                processedTransactionCount += outcome.transactionCount();
                institutionResults.add(new InstitutionSyncResult(
                        organizationCode, connection.getId(), outcome.aggregate().status(),
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
                    continue;
                }

                boolean advanced = accountSyncStateMapper.advanceIfOwner(
                        connection.getId(), organizationCode, AccountSyncStatus.SUCCESS.name(), null,
                        lease.jobName(), lease.businessDate(), lease.ownerId(), lease.leaseToken()
                ) == 1;
                if (!advanced) {
                    leaseLost = true;
                    break userLoop;
                }

                userHasSuccess = true;
                if (!outcome.affectedYearMonths().isEmpty()) {
                    affectedYearMonthsByUser
                            .computeIfAbsent(userId, id -> new TreeSet<>())
                            .addAll(outcome.affectedYearMonths());
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
                DailyFinancialSyncProvider.NTROPY,
                executionStatus,
                List.copyOf(successfulUserIds),
                affectedYearMonthsByUser.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()))),
                List.copyOf(partialFailedUserIds),
                List.copyOf(institutionResults),
                processedTransactionCount
        );
    }

    /** 사용자의 NTROPY 수시입출 계좌를 한 번만 조회해 기관코드 기준으로 그룹핑한다 (이슈 #233). */
    private Map<String, Account> findOrdinaryAccountsByOrganization(Long userId) {
        Map<String, Account> ordinaryAccountsByOrganization = new LinkedHashMap<>();
        for (Account account : accountMapper.findByUserIdAndProvider(userId, ConnectionProvider.NTROPY.name())) {
            if (account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST
                    && ORDINARY_DEPOSIT_TYPE_CODE.equals(account.getDepositTypeCode())) {
                ordinaryAccountsByOrganization.putIfAbsent(account.getOrganizationCode(), account);
            }
        }
        return ordinaryAccountsByOrganization;
    }

    /** 활성 사용자의 NTROPY 연결을 chunk 단위로 일괄 조회한다 (이슈 #233). */
    private Map<Long, CodefConnection> fetchConnectionsByUserId(List<Long> userIds) {
        Map<Long, CodefConnection> connectionsByUserId = new LinkedHashMap<>();
        for (List<Long> chunk : Batching.chunk(userIds, USER_ID_CHUNK_SIZE)) {
            for (CodefConnection connection : codefConnectionMapper.findByUserIdsAndProvider(chunk, ConnectionProvider.NTROPY.name())) {
                connectionsByUserId.put(connection.getUserId(), connection);
            }
        }
        return connectionsByUserId;
    }

    private InstitutionGenerationOutcome generateForInstitution(CodefConnection connection, String organizationCode,
                                                                 LocalDate businessDate, Account ordinaryAccount) {
        if (ordinaryAccount == null) {
            return InstitutionGenerationOutcome.failed(InstitutionAggregate.failed("ORDINARY_ACCOUNT_NOT_FOUND"));
        }

        AccountSyncState state = accountSyncStateMapper.findByConnectionAndOrganization(connection.getId(), organizationCode);
        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                state == null ? null : state.getLastSuccessfulSyncedAt(),
                ordinaryAccount.getLastTranDate(), businessDate, incrementalSyncPolicy
        );

        try {
            List<AccountTransaction> transactions = transactionGenerator.generate(
                    ordinaryAccount.getUserId(), ordinaryAccount.getId(), startDate, businessDate
            );
            if (!transactions.isEmpty()) {
                accountTransactionMapper.insertAll(transactions);
            }
            Set<YearMonth> affectedYearMonths = transactions.stream()
                    .map(AccountTransaction::getTranDate)
                    .filter(java.util.Objects::nonNull)
                    .map(YearMonth::from)
                    .collect(Collectors.toCollection(TreeSet::new));
            return new InstitutionGenerationOutcome(
                    InstitutionAggregate.success(), Set.copyOf(affectedYearMonths), transactions.size()
            );
        } catch (RuntimeException e) {
            return InstitutionGenerationOutcome.failed(InstitutionAggregate.failed("GENERATION_FAILED"));
        }
    }

    private static AccountSyncState pendingSyncState(Long codefConnectionId, String organizationCode) {
        AccountSyncState state = new AccountSyncState();
        state.setCodefConnectionId(codefConnectionId);
        state.setOrganizationCode(organizationCode);
        state.setLastStatus(AccountSyncStatus.PENDING);
        return state;
    }

    private record InstitutionGenerationOutcome(InstitutionAggregate aggregate, Set<YearMonth> affectedYearMonths,
                                                long transactionCount) {
        static InstitutionGenerationOutcome failed(InstitutionAggregate aggregate) {
            return new InstitutionGenerationOutcome(aggregate, Set.of(), 0);
        }
    }

    private record InstitutionAggregate(boolean hasFailure, String errorCode) {
        static InstitutionAggregate success() {
            return new InstitutionAggregate(false, null);
        }

        static InstitutionAggregate failed(String errorCode) {
            return new InstitutionAggregate(true, errorCode);
        }

        String status() {
            return hasFailure ? "PARTIAL_FAILED" : "SUCCESS";
        }
    }
}
