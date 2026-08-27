package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.config.IncrementalSyncPolicy;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;

class DailyNtropySyncServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 14);
    private static final LeaseHandle LEASE = new LeaseHandle(1L, "daily-sync-ntropy", BUSINESS_DATE, "owner-a", "token-a");

    @Test
    void generatesTransactionsAdvancesWatermarkAndMarksUserSuccessful() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "ntropy-cid", "[\"0088\"]"));
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        accountMapper.put(1L, ordinaryAccount(10L, 1L, "0088", LocalDate.of(2026, 8, 10)));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        RecordingAccountTransactionMapper transactionMapper = new RecordingAccountTransactionMapper();
        DailyNtropySyncService service = newService(
                connectionMapper, accountMapper, transactionMapper, syncStateMapper, alwaysTrueLease()
        );

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(List.of(1L), result.successfulUserIds());
        assertTrue(result.partialFailedUserIds().isEmpty());
        assertEquals("SUCCESS", result.executionStatus());
        assertTrue(result.processedTransactionCount() > 0);
        assertEquals(1, syncStateMapper.advanceCalls.size());
        assertTrue(transactionMapper.insertedBatches > 0);
        assertTrue(transactionMapper.inserted.stream().noneMatch(t -> t.getTranDate().isAfter(BUSINESS_DATE)));
        Set<YearMonth> generatedMonths = transactionMapper.inserted.stream()
                .map(AccountTransaction::getTranDate)
                .map(YearMonth::from)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(generatedMonths, new HashSet<>(result.affectedYearMonthsByUser().get(1L)));
    }

    @Test
    void marksUserPartialFailedAndDoesNotAdvanceWatermarkWhenOrdinaryAccountMissing() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "ntropy-cid", "[\"0088\"]"));
        FakeAccountMapper accountMapper = new FakeAccountMapper(); // 계좌를 하나도 넣지 않음
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        RecordingAccountTransactionMapper transactionMapper = new RecordingAccountTransactionMapper();
        DailyNtropySyncService service = newService(
                connectionMapper, accountMapper, transactionMapper, syncStateMapper, alwaysTrueLease()
        );

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(List.of(1L), result.partialFailedUserIds());
        assertTrue(syncStateMapper.advanceCalls.isEmpty());
        assertEquals("ORDINARY_ACCOUNT_NOT_FOUND", result.institutionResults().get(0).errorCode());
        assertEquals(1, syncStateMapper.statusCalls.size());
        assertEquals("PARTIAL_FAILED", syncStateMapper.statusCalls.get(0)[2]);
    }

    @Test
    void stopsImmediatelyWhenHeartbeatFailsAndReportsFailedExecution() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "ntropy-cid", "[\"0088\",\"0011\"]"));
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        accountMapper.put(1L, ordinaryAccount(10L, 1L, "0088", LocalDate.of(2026, 8, 10)));
        accountMapper.put(1L, ordinaryAccount(11L, 1L, "0011", LocalDate.of(2026, 8, 10)));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        RecordingAccountTransactionMapper transactionMapper = new RecordingAccountTransactionMapper();
        CountingLeaseService leaseService = new CountingLeaseService(2);
        DailyNtropySyncService service = newService(
                connectionMapper, accountMapper, transactionMapper, syncStateMapper, leaseService
        );

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals("FAILED", result.executionStatus());
        assertEquals(2, leaseService.heartbeatCalls);
    }

    @Test
    void firstTimeGenerationSucceedsWithNoPreexistingWatermarkRow() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "ntropy-cid", "[\"0088\"]"));
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        accountMapper.put(1L, ordinaryAccount(10L, 1L, "0088", null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        RecordingAccountTransactionMapper transactionMapper = new RecordingAccountTransactionMapper();
        DailyNtropySyncService service = newService(
                connectionMapper, accountMapper, transactionMapper, syncStateMapper, alwaysTrueLease()
        );

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals("SUCCESS", result.executionStatus());
        assertEquals(1, syncStateMapper.insertIfAbsentCalls);
    }

    private static DailyNtropySyncService newService(CodefConnectionMapper connectionMapper,
                                                      AccountMapper accountMapper,
                                                      AccountTransactionMapper transactionMapper,
                                                      AccountSyncStateMapper syncStateMapper,
                                                      BatchExecutionLeaseService leaseService) {
        return new DailyNtropySyncService(
                connectionMapper, accountMapper, transactionMapper, syncStateMapper, leaseService,
                new NtropyIncrementalTransactionGenerator(), new IncrementalSyncPolicy(1, 90)
        );
    }

    private static BatchExecutionLeaseService alwaysTrueLease() {
        return new CountingLeaseService(Integer.MAX_VALUE);
    }

    private static CodefConnection connection(Long id, Long userId, String connectedId, String registeredKeys) {
        CodefConnection connection = new CodefConnection();
        connection.setId(id);
        connection.setUserId(userId);
        connection.setProvider("NTROPY");
        connection.setConnectedId(connectedId);
        connection.setRegisteredInstitutionKeys(registeredKeys);
        return connection;
    }

    private static Account ordinaryAccount(Long id, Long userId, String organizationCode, LocalDate lastTranDate) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(userId);
        account.setOrganizationCode(organizationCode);
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("11");
        account.setLastTranDate(lastTranDate);
        return account;
    }

    private static class CountingLeaseService extends BatchExecutionLeaseService {

        private final int failFromCall;
        private int heartbeatCalls;

        CountingLeaseService(int failFromCall) {
            super(null, null);
            this.failFromCall = failFromCall;
        }

        @Override
        public boolean heartbeat(LeaseHandle lease) {
            heartbeatCalls++;
            return heartbeatCalls < failFromCall;
        }

    }

    private static class FakeCodefConnectionMapper implements CodefConnectionMapper {

        private final Map<Long, CodefConnection> byUserId = new HashMap<>();

        void put(Long userId, CodefConnection connection) {
            byUserId.put(userId, connection);
        }

        @Override
        public void insert(CodefConnection codefConnection) {
        }

        @Override
        public void insertIfAbsent(CodefConnection codefConnection) {
        }

        @Override
        public void upsert(CodefConnection codefConnection) {
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            return byUserId.get(userId);
        }

        @Override
        public List<CodefConnection> findByUserIdsAndProvider(List<Long> userIds, String provider) {
            return userIds.stream()
                    .map(userId -> findByUserIdAndProvider(userId, provider))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
    }

    private static class FakeAccountMapper implements AccountMapper {

        private final Map<Long, List<Account>> byUserId = new HashMap<>();

        void put(Long userId, Account account) {
            byUserId.computeIfAbsent(userId, id -> new ArrayList<>()).add(account);
        }

        @Override
        public void upsert(Account account) {
        }

        @Override
        public void upsertAll(List<Account> accounts) {
        }

        @Override
        public void updateAccountDetails(Account account) {
        }

        @Override
        public Account findByConnectionIdAndAccountNoHash(Long codefConnectionId, String accountNoHash) {
            return null;
        }

        @Override
        public List<Account> findByConnectionIdAndAccountNoHashes(Long codefConnectionId, List<String> accountNoHashes) {
            return List.of();
        }

        @Override
        public Account findByIdAndUserIdAndProvider(Long id, Long userId, String provider) {
            return null;
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            return byUserId.getOrDefault(userId, List.of());
        }

        @Override
        public boolean existsAnyByUserIdAndProvider(Long userId, String provider) {
            return !byUserId.getOrDefault(userId, List.of()).isEmpty();
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
        }
    }

    /**
     * 실제 ACCOUNT_SYNC_STATE의 "UPDATE-only" 특성을 흉내낸다: insertIfAbsent로 행을 만들지 않은
     * (connectionId, organizationCode)에는 advanceIfOwner가 0을 반환한다.
     */
    private static class FakeAccountSyncStateMapper implements AccountSyncStateMapper {

        private final Set<String> existingRows = new HashSet<>();
        private final List<Object[]> advanceCalls = new ArrayList<>();
        private final List<Object[]> statusCalls = new ArrayList<>();
        private int insertIfAbsentCalls;

        @Override
        public AccountSyncState findByConnectionAndOrganization(Long codefConnectionId, String organizationCode) {
            return null;
        }

        @Override
        public void insertIfAbsent(AccountSyncState state) {
            insertIfAbsentCalls++;
            existingRows.add(key(state.getCodefConnectionId(), state.getOrganizationCode()));
        }

        @Override
        public int advanceIfOwner(Long codefConnectionId, String organizationCode,
                                  String lastStatus, String lastErrorCode, String jobName,
                                  LocalDate businessDate, String ownerId, String leaseToken) {
            if (!existingRows.contains(key(codefConnectionId, organizationCode))) {
                return 0;
            }
            advanceCalls.add(new Object[]{codefConnectionId, organizationCode, lastStatus});
            return 1;
        }

        @Override
        public int markStatusIfOwner(Long codefConnectionId, String organizationCode,
                                     String lastStatus, String lastErrorCode, String jobName,
                                     LocalDate businessDate, String ownerId, String leaseToken) {
            if (!existingRows.contains(key(codefConnectionId, organizationCode))) {
                return 0;
            }
            statusCalls.add(new Object[]{codefConnectionId, organizationCode, lastStatus, lastErrorCode});
            return 1;
        }

        private static String key(Long codefConnectionId, String organizationCode) {
            return codefConnectionId + ":" + organizationCode;
        }
    }

    private static class RecordingAccountTransactionMapper implements AccountTransactionMapper {

        private int insertedBatches;
        private final List<AccountTransaction> inserted = new ArrayList<>();

        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            insertedBatches++;
            inserted.addAll(transactions);
        }

        @Override
        public List<AccountTransaction> findByAccountIdAndDateRange(Long accountId, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
        }

        @Override
        public LocalDate findMostRecentTransactionDate(Long codefConnectionId, String organizationCode) {
            return null;
        }
    }
}
