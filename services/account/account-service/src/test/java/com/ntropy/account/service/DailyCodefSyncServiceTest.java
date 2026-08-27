package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.config.BirthDateEncryptionProperties;
import com.ntropy.account.config.IncrementalSyncPolicy;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.exception.LeaseLostException;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.security.BirthDateCipher;
import com.ntropy.account.service.AccountCollectionService.AccountCollectionOutcome;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;

class DailyCodefSyncServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 14);
    private static final LeaseHandle LEASE = new LeaseHandle(1L, "daily-sync-codef", BUSINESS_DATE, "owner-a", "token-a");

    @Test
    void marksUserSuccessfulAndAdvancesWatermarkWhenAllAccountsSucceed() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK, List.of(
                outcome(AccountCollectionOutcome.Status.SUCCESS, 5)
        ));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(List.of(1L), result.successfulUserIds());
        assertTrue(result.partialFailedUserIds().isEmpty());
        assertEquals("SUCCESS", result.executionStatus());
        assertEquals(5L, result.processedTransactionCount());
        assertEquals(1, syncStateMapper.advanceCalls.size());
        assertTrue(result.affectedYearMonthsByUser().containsKey(1L));
    }

    @Test
    void firstTimeSyncSucceedsWithNoPreexistingWatermarkRow() {
        // FakeAccountSyncStateMapper는 insertIfAbsent를 거치지 않은 (connectionId, orgCode)에 대해
        // advanceIfOwner가 0을 반환하도록 실제 UPDATE-only 동작을 흉내낸다 — insertIfAbsent를
        // 빠뜨리면 최초 동기화가 항상 lease 상실로 오판되는 회귀를 이 테스트가 잡는다.
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK, List.of(
                outcome(AccountCollectionOutcome.Status.SUCCESS, 3)
        ));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals("SUCCESS", result.executionStatus());
        assertEquals(List.of(1L), result.successfulUserIds());
        assertEquals(1, syncStateMapper.insertIfAbsentCalls);
    }

    @Test
    void doesNotAdvanceWatermarkAndMarksUserPartialFailedOnRealFailure() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK, List.of(
                outcome(AccountCollectionOutcome.Status.FAILED, 0)
        ));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertTrue(result.successfulUserIds().isEmpty());
        assertEquals(List.of(1L), result.partialFailedUserIds());
        assertEquals("PARTIAL_FAILED", result.executionStatus());
        assertTrue(syncStateMapper.advanceCalls.isEmpty(), "실패한 기관의 watermark는 전진하면 안 됩니다");
        assertEquals(1L, result.institutionResults().get(0).codefConnectionId());
        assertEquals(1, syncStateMapper.statusCalls.size(), "실패 상태는 watermark와 별도로 저장해야 합니다");
        assertEquals("PARTIAL_FAILED", syncStateMapper.statusCalls.get(0)[2]);
        assertEquals("COLLECTION_FAILED", syncStateMapper.statusCalls.get(0)[3]);
    }

    @Test
    void advancesWatermarkAsSkippedStatusWhenOnlySkippedCredentialRequired() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0023\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SC_BANK, List.of(
                outcome(AccountCollectionOutcome.Status.SKIPPED_CREDENTIAL_REQUIRED, 0)
        ));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(1, syncStateMapper.advanceCalls.size(), "SKIPPED_CREDENTIAL_REQUIRED만 있어도 watermark는 전진해야 합니다");
        assertEquals("SKIPPED_CREDENTIAL_REQUIRED", syncStateMapper.advanceCalls.get(0)[2],
                "watermark의 last_status에 SKIPPED_CREDENTIAL_REQUIRED가 남아야 합니다");
        assertTrue(result.successfulUserIds().isEmpty(), "실제로 수집된 게 없으면 성공 사용자로 집계하지 않습니다");
        assertTrue(result.partialFailedUserIds().isEmpty());
        assertEquals("SKIPPED_CREDENTIAL_REQUIRED", result.institutionResults().get(0).status(),
                "SKIPPED 사실이 institutionResults에도 남아야 합니다");
    }

    @Test
    void stopsProcessingImmediatelyWhenHeartbeatFailsAndReportsFailedExecution() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\",\"0011\"]", null, null));
        connectionMapper.put(2L, connection(2L, 2L, "cid2", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        collectionService.stub(1L, PersonalBank.NH_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        collectionService.stub(2L, PersonalBank.SHINHAN_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        CountingLeaseService leaseService = new CountingLeaseService(2); // 2번째 heartbeat부터 실패
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, leaseService);

        DailyFinancialSyncResult result = service.synchronize(List.of(1L, 2L), BUSINESS_DATE, LEASE);

        assertEquals("FAILED", result.executionStatus());
        assertEquals(2, leaseService.heartbeatCalls, "두 번째 heartbeat 실패 직후 멈춰야 합니다");
    }

    @Test
    void abortsEntirelyWhenHeartbeatFailsMidAccountProcessing() {
        // AccountCollectionService.collectForDailySync 내부(계좌 루프)에서 heartbeat가 실패하면
        // LeaseLostException이 던져진다. 이 기관만 실패 처리하지 말고 전체 실행을 즉시 중단해야 한다.
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\",\"0011\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stubThrowsLeaseLost(1L, PersonalBank.SHINHAN_BANK);
        collectionService.stub(1L, PersonalBank.NH_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals("FAILED", result.executionStatus());
        assertTrue(syncStateMapper.advanceCalls.isEmpty(), "lease를 잃은 뒤에는 어떤 watermark도 전진하면 안 됩니다");
    }

    @Test
    void marksInstitutionFailedWhenEncryptedBirthDateIsMissingForRequiredBank() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0004\"]", null, null)); // KB인데 birthDate 없음
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(List.of(1L), result.partialFailedUserIds());
        assertTrue(syncStateMapper.advanceCalls.isEmpty());
        assertEquals(1, result.institutionResults().size());
        assertEquals("CREDENTIAL_MISSING", result.institutionResults().get(0).errorCode());
    }

    @Test
    void isolatesBirthDateDecryptionFailureToThisInstitutionOnly() {
        // 저장된 IV/암호문이 손상됐거나 키 버전이 바뀐 경우를 흉내낸다: 유효한 Base64이지만
        // AES-GCM 인증 태그가 맞지 않아 복호화 자체가 실패한다. 이 예외가 배치 전체를 중단시키면
        // 안 되고, 이 기관(KB)만 CREDENTIAL_DECRYPT_FAILED로 격리한 채 다른 사용자는 계속 처리돼야 한다.
        String corruptCiphertext = Base64.getEncoder().encodeToString(new byte[28]);
        String iv = Base64.getEncoder().encodeToString(new byte[12]);

        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0004\"]", corruptCiphertext, iv));
        connectionMapper.put(2L, connection(2L, 2L, "cid2", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(2L, PersonalBank.SHINHAN_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 2)));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L, 2L), BUSINESS_DATE, LEASE);

        assertEquals("PARTIAL_FAILED", result.executionStatus(), "배치 전체가 아니라 사용자 1만 실패해야 합니다");
        assertEquals(List.of(1L), result.partialFailedUserIds());
        assertEquals(List.of(2L), result.successfulUserIds());
        boolean hasDecryptFailure = result.institutionResults().stream()
                .anyMatch(r -> "CREDENTIAL_DECRYPT_FAILED".equals(r.errorCode()));
        assertTrue(hasDecryptFailure);
    }

    @Test
    void keepsInstitutionFailuresSeparateByInternalConnectionId() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(11L, 1L, "cid1", "[\"0088\"]", null, null));
        connectionMapper.put(2L, connection(22L, 2L, "cid2", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK,
                List.of(outcome(AccountCollectionOutcome.Status.FAILED, 0)));
        collectionService.stub(2L, PersonalBank.SHINHAN_BANK,
                List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        DailyCodefSyncService service = newService(
                connectionMapper, syncStateMapper, collectionService, alwaysTrueLease()
        );

        DailyFinancialSyncResult result = service.synchronize(List.of(1L, 2L), BUSINESS_DATE, LEASE);

        assertEquals(2, result.institutionResults().size());
        assertEquals(Set.of(11L, 22L), result.institutionResults().stream()
                .map(DailyFinancialSyncResult.InstitutionSyncResult::codefConnectionId)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void doesNotReportQueriedMonthWhenNoTransactionWasReturned() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK,
                List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 0)));
        DailyCodefSyncService service = newService(
                connectionMapper, syncStateMapper, collectionService, alwaysTrueLease()
        );

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertTrue(result.affectedYearMonthsByUser().isEmpty());
    }

    private static DailyCodefSyncService newService(CodefConnectionMapper connectionMapper,
                                                     AccountSyncStateMapper syncStateMapper,
                                                     AccountCollectionService collectionService,
                                                     BatchExecutionLeaseService leaseService) {
        BirthDateCipher cipher = new BirthDateCipher(
                new BirthDateEncryptionProperties("EnZnHF6ULrhAjwHSJs5+2lizbiv7BHiB+5sZ4YIKEmc=", 1)
        );
        return new DailyCodefSyncService(
                connectionMapper, syncStateMapper, new NoopAccountTransactionMapper(), collectionService,
                leaseService, cipher, new IncrementalSyncPolicy(1, 90)
        );
    }

    private static BatchExecutionLeaseService alwaysTrueLease() {
        return new CountingLeaseService(Integer.MAX_VALUE);
    }

    private static AccountCollectionOutcome outcome(AccountCollectionOutcome.Status status, int transactionCount) {
        Account account = new Account();
        account.setId(100L);
        return new AccountCollectionOutcome(account, status, status == AccountCollectionOutcome.Status.FAILED
                ? "COLLECTION_FAILED" : null, transactionCount,
                transactionCount > 0 ? Set.of(YearMonth.from(BUSINESS_DATE)) : Set.of());
    }

    private static CodefConnection connection(Long id, Long userId, String connectedId, String registeredKeys,
                                              String birthDateCiphertext, String birthDateIv) {
        CodefConnection connection = new CodefConnection();
        connection.setId(id);
        connection.setUserId(userId);
        connection.setProvider("CODEF");
        connection.setConnectedId(connectedId);
        connection.setRegisteredInstitutionKeys(registeredKeys);
        connection.setBirthDateCiphertext(birthDateCiphertext);
        connection.setBirthDateIv(birthDateIv);
        connection.setBirthDateKeyVersion(birthDateCiphertext == null ? null : 1);
        return connection;
    }

    /** heartbeat를 callLimit번째 호출부터 실패시킨다(1-based). Integer.MAX_VALUE면 항상 성공. */
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

    private static class StubAccountCollectionService extends AccountCollectionService {

        private final Map<String, List<AccountCollectionOutcome>> byKey = new HashMap<>();
        private final Set<String> leaseLostKeys = new HashSet<>();

        StubAccountCollectionService() {
            super(null, null, null, null, null, null, null);
        }

        void stub(Long userId, PersonalBank bank, List<AccountCollectionOutcome> outcomes) {
            byKey.put(userId + ":" + bank.getOrganizationCode(), outcomes);
        }

        void stubThrowsLeaseLost(Long userId, PersonalBank bank) {
            leaseLostKeys.add(userId + ":" + bank.getOrganizationCode());
        }

        @Override
        public List<AccountCollectionOutcome> collectForDailySync(Long userId, PersonalBank bank, String birthDate,
                                                                   LocalDate transactionStartDate,
                                                                   LocalDate transactionEndDate,
                                                                   BooleanSupplier heartbeat) {
            String key = userId + ":" + bank.getOrganizationCode();
            if (leaseLostKeys.contains(key)) {
                throw new LeaseLostException();
            }
            return byKey.getOrDefault(key, List.of());
        }

        @Override
        public List<AccountCollectionOutcome> collectForDailySync(Long userId, PersonalBank bank,
                                                                   CodefConnection connection, String birthDate,
                                                                   LocalDate transactionStartDate,
                                                                   LocalDate transactionEndDate,
                                                                   BooleanSupplier heartbeat) {
            return collectForDailySync(userId, bank, birthDate, transactionStartDate, transactionEndDate, heartbeat);
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

    /**
     * 실제 ACCOUNT_SYNC_STATE의 "UPDATE-only" 특성을 흉내낸다: insertIfAbsent로 행을 만들지 않은
     * (connectionId, organizationCode)에는 advanceIfOwner가 0을 반환한다. 이 모델링이 없으면
     * 최초 동기화가 항상 lease 상실로 오판되는 회귀를 테스트가 잡아내지 못한다.
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

    private static class NoopAccountTransactionMapper implements AccountTransactionMapper {

        @Override
        public void insertAll(List<com.ntropy.account.domain.entity.AccountTransaction> transactions) {
        }

        @Override
        public List<com.ntropy.account.domain.entity.AccountTransaction> findByAccountIdAndDateRange(
                Long accountId, LocalDate startDate, LocalDate endDate) {
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
