package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;

class VirtualAccountRegenerationServiceTest {

    private static final Long TARGET_USER_ID = 9_000_046_001L;
    private static final Long OTHER_USER_ID = 9_000_046_002L;

    @Test
    void replacesOnlyTargetUsersNtropyDataWithoutDuplicatesAndKeepsCodefData() {
        // 계좌의 codef_connection_id -> provider 조회를 실제 SQL의 서브쿼리 조인과 동일하게 흉내내는 공유 테이블.
        Map<Long, String> connectionProviders = new HashMap<>();
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper(connectionProviders);
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper(connectionProviders);
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper(accountMapper);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(LocalDate.of(2026, 6, 30).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService dataService = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper), accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), clock, null
        );
        VirtualAccountRegenerationService regenerationService =
                new VirtualAccountRegenerationService(accountMapper, transactionMapper, dataService);

        // 실제 CODEF 연결·계좌·거래를 대상 사용자에게 미리 심어둔다.
        CodefConnection codefConnection = new CodefConnection();
        codefConnection.setUserId(TARGET_USER_ID);
        codefConnection.setProvider(ConnectionProvider.CODEF.name());
        codefConnection.setConnectedId("codef-connected-id");
        connectionMapper.upsert(codefConnection);
        Account codefAccount = new Account();
        codefAccount.setCodefConnectionId(codefConnection.getId());
        codefAccount.setUserId(TARGET_USER_ID);
        codefAccount.setAccountNoHash("codef-hash");
        codefAccount.setAccountGroup(com.ntropy.account.domain.AccountGroup.DEPOSIT_TRUST);
        accountMapper.upsert(codefAccount);
        AccountTransaction codefTransaction = new AccountTransaction();
        codefTransaction.setAccountId(codefAccount.getId());
        codefTransaction.setFingerprint("codef-fp");
        codefTransaction.setTranDate(LocalDate.of(2026, 6, 1));
        transactionMapper.insertAll(List.of(codefTransaction));

        // 다른 사용자의 NTROPY 데이터도 미리 생성해둔다.
        dataService.generateForUser(OTHER_USER_ID, PersonalBank.NH_BANK);
        int otherUserAccountsBefore = countByUser(accountMapper, OTHER_USER_ID);
        int otherUserTransactionsBefore = countTransactionsByUser(accountMapper, transactionMapper, OTHER_USER_ID);

        // 대상 사용자의 최초 NTROPY 가상계좌 생성.
        dataService.generateForUser(TARGET_USER_ID, PersonalBank.SHINHAN_BANK);
        Long firstConnectionId = connectionMapper.findByUserIdAndProvider(
                TARGET_USER_ID, ConnectionProvider.NTROPY.name()
        ).getId();
        List<Account> firstNtropyAccounts = accountMapper.findByUserIdAndProvider(
                TARGET_USER_ID, ConnectionProvider.NTROPY.name()
        );
        int firstAccountCount = firstNtropyAccounts.size();
        int firstTransactionCount = transactionCountFor(transactionMapper, firstNtropyAccounts);

        // 은행을 바꿔 재등록한다.
        regenerationService.regenerateForUser(TARGET_USER_ID, PersonalBank.NH_BANK);

        Long secondConnectionId = connectionMapper.findByUserIdAndProvider(
                TARGET_USER_ID, ConnectionProvider.NTROPY.name()
        ).getId();
        assertEquals(firstConnectionId, secondConnectionId, "NTROPY CODEF_CONNECTION 행은 재사용돼야 한다");

        List<Account> ntropyAccountsAfter = accountMapper.findByUserIdAndProvider(
                TARGET_USER_ID, ConnectionProvider.NTROPY.name()
        );
        assertEquals(firstAccountCount, ntropyAccountsAfter.size(), "재등록 후에도 계좌 수는 중복 없이 동일해야 한다");
        assertTrue(ntropyAccountsAfter.stream()
                .allMatch(account -> account.getOrganizationCode().equals(PersonalBank.NH_BANK.getOrganizationCode())));
        assertEquals(firstTransactionCount, transactionCountFor(transactionMapper, ntropyAccountsAfter),
                "재등록 후에도 거래 수는 중복 없이 동일해야 한다");

        // 실제 CODEF 데이터와 다른 사용자 NTROPY 데이터는 그대로 유지된다.
        assertTrue(accountMapper.store.values().stream()
                .anyMatch(account -> account.getId().equals(codefAccount.getId())));
        assertTrue(transactionMapper.store.values().stream()
                .anyMatch(transaction -> transaction.getAccountId().equals(codefAccount.getId())));
        assertEquals(otherUserAccountsBefore, countByUser(accountMapper, OTHER_USER_ID));
        assertEquals(otherUserTransactionsBefore,
                countTransactionsByUser(accountMapper, transactionMapper, OTHER_USER_ID));
    }

    @Test
    void replacesDataWithoutDuplicatesWhenRegisteringSameBankAgain() {
        Map<Long, String> connectionProviders = new HashMap<>();
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper(connectionProviders);
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper(connectionProviders);
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper(accountMapper);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(LocalDate.of(2026, 6, 30).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService dataService = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper), accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), clock, null
        );
        VirtualAccountRegenerationService regenerationService =
                new VirtualAccountRegenerationService(accountMapper, transactionMapper, dataService);

        dataService.generateForUser(TARGET_USER_ID, PersonalBank.SHINHAN_BANK);
        int firstAccountCount = accountMapper.findByUserIdAndProvider(
                TARGET_USER_ID, ConnectionProvider.NTROPY.name()
        ).size();

        // 같은 은행으로 다시 등록해도 계좌·거래가 늘어나지 않아야 한다.
        regenerationService.regenerateForUser(TARGET_USER_ID, PersonalBank.SHINHAN_BANK);
        List<Account> accountsAfter = accountMapper.findByUserIdAndProvider(
                TARGET_USER_ID, ConnectionProvider.NTROPY.name()
        );

        assertEquals(firstAccountCount, accountsAfter.size());
        assertTrue(accountsAfter.stream()
                .allMatch(account -> account.getOrganizationCode().equals(PersonalBank.SHINHAN_BANK.getOrganizationCode())));
    }

    private static int countByUser(InMemoryAccountMapper accountMapper, Long userId) {
        return (int) accountMapper.store.values().stream()
                .filter(account -> userId.equals(account.getUserId())).count();
    }

    private static int countTransactionsByUser(InMemoryAccountMapper accountMapper,
                                               InMemoryAccountTransactionMapper transactionMapper, Long userId) {
        List<Account> accounts = accountMapper.store.values().stream()
                .filter(account -> userId.equals(account.getUserId())).toList();
        return transactionCountFor(transactionMapper, accounts);
    }

    private static int transactionCountFor(InMemoryAccountTransactionMapper transactionMapper,
                                           List<Account> accounts) {
        var accountIds = accounts.stream().map(Account::getId).toList();
        return (int) transactionMapper.store.values().stream()
                .filter(transaction -> accountIds.contains(transaction.getAccountId()))
                .count();
    }

    private static class InMemoryCodefConnectionMapper implements CodefConnectionMapper {

        private final Map<String, CodefConnection> store = new HashMap<>();
        private final Map<Long, String> connectionProviders;
        private long nextId = 1;

        InMemoryCodefConnectionMapper(Map<Long, String> connectionProviders) {
            this.connectionProviders = connectionProviders;
        }

        @Override
        public void insert(CodefConnection connection) {
            upsert(connection);
        }

        @Override
        public void insertIfAbsent(CodefConnection connection) {
            store.computeIfAbsent(key(connection), ignored -> {
                connection.setId(nextId++);
                connectionProviders.put(connection.getId(), connection.getProvider());
                return connection;
            });
        }

        @Override
        public void upsert(CodefConnection connection) {
            CodefConnection existing = store.get(key(connection));
            if (existing == null) {
                connection.setId(nextId++);
            } else {
                connection.setId(existing.getId());
            }
            connectionProviders.put(connection.getId(), connection.getProvider());
            store.put(key(connection), connection);
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            return store.get(userId + ":" + provider);
        }

        @Override
        public List<CodefConnection> findByUserIdsAndProvider(List<Long> userIds, String provider) {
            return userIds.stream()
                    .map(userId -> findByUserIdAndProvider(userId, provider))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        private static String key(CodefConnection connection) {
            return connection.getUserId() + ":" + connection.getProvider();
        }
    }

    private static class InMemoryAccountMapper implements AccountMapper {

        private final Map<String, Account> store = new LinkedHashMap<>();
        private final Map<Long, String> connectionProviders;
        private long nextId = 1;

        InMemoryAccountMapper(Map<Long, String> connectionProviders) {
            this.connectionProviders = connectionProviders;
        }

        @Override
        public void upsert(Account account) {
            String key = key(account.getCodefConnectionId(), account.getAccountNoHash());
            Account existing = store.get(key);
            if (existing == null) {
                account.setId(nextId++);
            } else {
                account.setId(existing.getId());
            }
            store.put(key, account);
        }

        @Override
        public void upsertAll(List<Account> accounts) {
            accounts.forEach(this::upsert);
        }

        @Override
        public void updateAccountDetails(Account account) {
        }

        @Override
        public Account findByConnectionIdAndAccountNoHash(Long codefConnectionId, String accountNoHash) {
            return store.get(key(codefConnectionId, accountNoHash));
        }

        @Override
        public List<Account> findByConnectionIdAndAccountNoHashes(Long codefConnectionId, List<String> accountNoHashes) {
            return accountNoHashes.stream()
                    .map(hash -> findByConnectionIdAndAccountNoHash(codefConnectionId, hash))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public Account findByIdAndUserIdAndProvider(Long id, Long userId, String provider) {
            return store.values().stream()
                    .filter(account -> id.equals(account.getId()) && userId.equals(account.getUserId())
                            && provider.equals(connectionProviders.get(account.getCodefConnectionId())))
                    .findFirst().orElse(null);
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream()
                    .filter(account -> userId.equals(account.getUserId())
                            && provider.equals(connectionProviders.get(account.getCodefConnectionId())))
                    .toList();
        }

        @Override
        public boolean existsAnyByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream()
                    .anyMatch(account -> userId.equals(account.getUserId())
                            && provider.equals(connectionProviders.get(account.getCodefConnectionId())));
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
            store.values().removeIf(account -> userId.equals(account.getUserId())
                    && provider.equals(connectionProviders.get(account.getCodefConnectionId())));
        }

        private static String key(Long connectionId, String accountNoHash) {
            return connectionId + ":" + accountNoHash;
        }
    }

    private static class InMemoryAccountTransactionMapper implements AccountTransactionMapper {

        private final Map<String, AccountTransaction> store = new LinkedHashMap<>();
        private final InMemoryAccountMapper accountMapper;

        InMemoryAccountTransactionMapper(InMemoryAccountMapper accountMapper) {
            this.accountMapper = accountMapper;
        }

        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            for (AccountTransaction transaction : transactions) {
                String key = transaction.getAccountId() + ":" + transaction.getFingerprint();
                store.put(key, transaction);
            }
        }

        @Override
        public List<AccountTransaction> findByAccountIdAndDateRange(Long accountId, LocalDate startDate,
                                                                    LocalDate endDate) {
            List<AccountTransaction> result = new ArrayList<>();
            for (AccountTransaction transaction : store.values()) {
                if (accountId.equals(transaction.getAccountId())
                        && !transaction.getTranDate().isBefore(startDate)
                        && !transaction.getTranDate().isAfter(endDate)) {
                    result.add(transaction);
                }
            }
            return result;
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
            var matchingAccountIds = accountMapper.findByUserIdAndProvider(userId, provider).stream()
                    .map(Account::getId)
                    .toList();
            store.values().removeIf(transaction -> matchingAccountIds.contains(transaction.getAccountId()));
        }

        @Override
        public LocalDate findMostRecentTransactionDate(Long codefConnectionId, String organizationCode) {
            return null;
        }
    }
}
