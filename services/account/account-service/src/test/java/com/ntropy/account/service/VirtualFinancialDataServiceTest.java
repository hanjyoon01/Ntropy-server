package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.port.user.SeededVirtualUser;
import com.ntropy.account.port.user.SeededVirtualUserBatch;
import com.ntropy.account.port.user.UserPort;
import com.ntropy.account.port.user.VirtualDatasetExecutionContext;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;
import com.ntropy.common.domain.UserScope;

class VirtualFinancialDataServiceTest {

    // 실제 USERS.user_id는 AUTO_INCREMENT 값이라 순번과 무관하다는 것을 드러내기 위해 순번과 다른 배수로 잡는다.
    private static long userIdFor(int ordinal) {
        return 8_000_000_000L + ordinal * 3L;
    }

    private static List<SeededVirtualUser> fiftyUserDataset() {
        List<SeededVirtualUser> users = new ArrayList<>();
        for (int ordinal = 1; ordinal <= 50; ordinal++) {
            users.add(new SeededVirtualUser(userIdFor(ordinal), ordinal));
        }
        return users;
    }

    @Test
    void generatesExpectedDatasetAndIsIdempotent() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper(accountMapper);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        // 월말을 기준일로 고정해 "현재 월"이 항상 완결된 3개월 창이 되도록 하고, 기존 고정 건수 검증값을 그대로 유지한다.
        Clock clock = Clock.fixed(LocalDate.of(2020, 1, 1).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper),
                accountMapper,
                transactionMapper,
                new VirtualFinancialTransactionGenerator(),
                clock,
                null // generateForUsers()를 직접 호출하므로 UserPort는 쓰이지 않는다.
        );
        List<SeededVirtualUser> users = fiftyUserDataset();
        VirtualDatasetExecutionContext context = new VirtualDatasetExecutionContext(
                "FIN-005-test-v1", LocalDate.of(2026, 6, 30), 1L
        );

        GenerationSummary first = service.generateForUsers(users, context);
        GenerationSummary second = service.generateForUsers(users, context);

        assertEquals(50, first.users());
        assertEquals(150, first.accounts());
        assertEquals(5, first.incomeCounterparties());
        assertEquals(15_300, first.transactions());
        assertEquals(first, second);
        assertEquals(50, connectionMapper.store.size());
        assertEquals(150, accountMapper.store.size());
        assertEquals(15_300, transactionMapper.store.size());
        for (SeededVirtualUser user : users) {
            assertEquals(1, countAccounts(accountMapper, user.userId(), AccountGroup.DEPOSIT_TRUST, "11"));
            assertEquals(1, countAccounts(accountMapper, user.userId(), AccountGroup.DEPOSIT_TRUST, "12"));
            assertEquals(1, countAccounts(accountMapper, user.userId(), AccountGroup.LOAN, "40"));
        }

        // 모든 사용자가 적금·대출 계좌를 동시에 갖는다.
        Account installmentAccount = accountMapper.store.values().stream()
                .filter(account -> account.getUserId() == userIdFor(1)
                        && account.getAccountGroup() == com.ntropy.account.domain.AccountGroup.DEPOSIT_TRUST
                        && "12".equals(account.getDepositTypeCode()))
                .findFirst().orElseThrow();
        assertEquals(null, installmentAccount.getLoanContractPrincipal());
        assertEquals("청년희망적금", installmentAccount.getAccountName());
        assertEquals(java.math.BigDecimal.valueOf(230, 2), installmentAccount.getInterestRate());
        assertEquals(LocalDate.of(2028, 6, 30), installmentAccount.getMaturityDate());
        assertEquals(LocalDate.of(2026, 7, 25), installmentAccount.getNextPaymentDate());
        assertEquals(lastTransactionDate(transactionMapper, installmentAccount), installmentAccount.getLastTranDate());

        Account loanAccount = accountMapper.store.values().stream()
                .filter(account -> account.getUserId() == userIdFor(1)
                        && account.getAccountGroup() == com.ntropy.account.domain.AccountGroup.LOAN)
                .findFirst().orElseThrow();
        assertEquals("주택담보대출", loanAccount.getAccountName());
        assertEquals(java.math.BigDecimal.valueOf(55_000_000L), loanAccount.getLoanContractPrincipal());
        assertEquals(java.math.BigDecimal.valueOf(340, 2), loanAccount.getInterestRate());
        assertEquals(LocalDate.of(2036, 6, 30), loanAccount.getMaturityDate());
        assertEquals(LocalDate.of(2026, 7, 25), loanAccount.getNextPaymentDate());
        assertEquals(lastTransactionDate(transactionMapper, loanAccount), loanAccount.getLastTranDate());
    }

    @Test
    void generateDelegatesToVirtualUserQueryClientDatasetUsingContextReferenceDate() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper(accountMapper);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        // Clock은 generate()/generateForUsers() 경로에서 쓰이지 않아야 하므로, context와 다른 날짜로 고정해 둔다.
        Clock clock = Clock.fixed(LocalDate.of(2099, 1, 1).atStartOfDay(zone).toInstant(), zone);
        List<SeededVirtualUser> users = List.of(
                new SeededVirtualUser(7_000_000_001L, 1),
                new SeededVirtualUser(7_000_000_002L, 2),
                new SeededVirtualUser(7_000_000_003L, 3),
                new SeededVirtualUser(7_000_000_004L, 4)
        );
        VirtualDatasetExecutionContext context = new VirtualDatasetExecutionContext(
                "FIN-005-test-v2", LocalDate.of(2026, 6, 30), 42L
        );
        UserPort stubUserPort = new UserPort() {
            @Override
            public List<Long> findActiveUserIds(UserScope scope) {
                throw new UnsupportedOperationException("이 테스트에서는 사용하지 않습니다");
            }

            @Override
            public SeededVirtualUserBatch findSeededVirtualUsers() {
                return new SeededVirtualUserBatch(context, users);
            }
        };
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper), accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), clock, stubUserPort
        );

        GenerationSummary summary = service.generate();

        assertEquals(4, summary.users());
        assertEquals(12, summary.accounts());
        assertEquals(context.referenceDate(), summary.endDate());
        assertEquals(1_224, summary.transactions());
    }

    @Test
    void handlesLeapYearReferenceDateForProductConditionDatesWithoutError() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper(accountMapper);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        // 2028-02-29(윤년)를 기준일로 삼아도 예외 없이 만기일·다음납입일이 계산돼야 한다.
        Clock clock = Clock.fixed(LocalDate.of(2028, 2, 29).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper), accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), clock, null
        );

        assertDoesNotThrow(() -> service.generateForUser(9_000_046_001L, com.ntropy.account.domain.PersonalBank.NH_BANK));

        Account loanOrInstallmentAccount = accountMapper.store.values().stream()
                .filter(account -> account.getNextPaymentDate() != null)
                .findFirst().orElseThrow();
        assertEquals(LocalDate.of(2028, 3, 25), loanOrInstallmentAccount.getNextPaymentDate());
        assertFalse(loanOrInstallmentAccount.getMaturityDate().isBefore(LocalDate.of(2028, 2, 29)));
    }

    @Test
    void usesCurrentMonthTwentyFifthBeforeDefaultPaymentDay() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper(accountMapper);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(LocalDate.of(2026, 6, 15).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper), accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), clock, null
        );

        service.generateForUser(9_000_046_001L, com.ntropy.account.domain.PersonalBank.NH_BANK);

        Account installmentAccount = accountMapper.store.values().stream()
                .filter(account -> "12".equals(account.getDepositTypeCode()))
                .findFirst().orElseThrow();
        assertEquals("청년희망적금", installmentAccount.getAccountName());
        assertEquals(LocalDate.of(2026, 6, 25), installmentAccount.getNextPaymentDate());
        assertEquals(lastTransactionDate(transactionMapper, installmentAccount), installmentAccount.getLastTranDate());

        Account loanAccount = accountMapper.store.values().stream()
                .filter(account -> account.getAccountGroup() == AccountGroup.LOAN)
                .findFirst().orElseThrow();
        assertEquals("주택담보대출", loanAccount.getAccountName());
    }

    @Test
    void migratesLegacyTwoAccountLayoutBeforeGeneratingThreeAccountLayout() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper(accountMapper);
        VirtualConnectionService connectionService = new VirtualConnectionService(connectionMapper);
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                connectionService, accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), Clock.systemUTC(), null
        );
        long userId = userIdFor(26);
        CodefConnection connection = connectionService.getOrCreateConnection(userId);
        Account ordinary = legacyAccount(connection, userId, 26, 1, AccountGroup.DEPOSIT_TRUST, "11");
        Account legacyLoan = legacyAccount(connection, userId, 26, 2, AccountGroup.LOAN, "40");
        accountMapper.upsert(ordinary);
        accountMapper.upsert(legacyLoan);

        AccountTransaction legacyTransaction = new AccountTransaction();
        legacyTransaction.setAccountId(legacyLoan.getId());
        legacyTransaction.setFingerprint("legacy-loan-transaction");
        legacyTransaction.setTransactionCategory(AccountTransactionCategory.LOAN);
        legacyTransaction.setTranDate(LocalDate.of(2026, 4, 25));
        legacyTransaction.setTranTime(LocalTime.of(21, 1));
        legacyTransaction.setOutAmount(BigDecimal.valueOf(500_000L));
        legacyTransaction.setInAmount(BigDecimal.ZERO);
        legacyTransaction.setAfterBalance(BigDecimal.valueOf(15_000_000L));
        transactionMapper.insertAll(List.of(legacyTransaction));

        VirtualDatasetExecutionContext context = new VirtualDatasetExecutionContext(
                "FIN-005-test-migration", LocalDate.of(2026, 6, 30), 1L
        );
        GenerationSummary first = service.generateForUsers(
                List.of(new SeededVirtualUser(userId, 26)), context
        );
        GenerationSummary second = service.generateForUsers(
                List.of(new SeededVirtualUser(userId, 26)), context
        );

        assertEquals(first, second);
        assertEquals(3, accountMapper.store.size());
        assertEquals(306, transactionMapper.store.size());
        assertEquals(1, countAccounts(accountMapper, userId, AccountGroup.DEPOSIT_TRUST, "11"));
        assertEquals(1, countAccounts(accountMapper, userId, AccountGroup.DEPOSIT_TRUST, "12"));
        assertEquals(1, countAccounts(accountMapper, userId, AccountGroup.LOAN, "40"));
        assertFalse(transactionMapper.store.values().stream()
                .anyMatch(transaction -> "legacy-loan-transaction".equals(transaction.getFingerprint())));
    }

    private static Account legacyAccount(CodefConnection connection, long userId, int userOrdinal,
                                         int accountOrdinal, AccountGroup accountGroup, String depositTypeCode) {
        String rawAccountNo = String.format(Locale.ROOT, "46%03d%07d", userOrdinal, accountOrdinal);
        Account account = new Account();
        account.setCodefConnectionId(connection.getId());
        account.setUserId(userId);
        account.setOrganizationCode(PersonalBank.SHINHAN_BANK.getOrganizationCode());
        account.setAccountGroup(accountGroup);
        account.setDepositTypeCode(depositTypeCode);
        account.setAccountNoMasked(rawAccountNo);
        account.setAccountNoHash(AccountNoHash.hash(
                PersonalBank.SHINHAN_BANK.getOrganizationCode(), "FIN-005-v1:" + rawAccountNo
        ));
        return account;
    }

    private static long countAccounts(InMemoryAccountMapper accountMapper, long userId,
                                      AccountGroup accountGroup, String depositTypeCode) {
        return accountMapper.store.values().stream()
                .filter(account -> account.getUserId() == userId)
                .filter(account -> account.getAccountGroup() == accountGroup)
                .filter(account -> depositTypeCode.equals(account.getDepositTypeCode()))
                .count();
    }

    private static LocalDate lastTransactionDate(InMemoryAccountTransactionMapper transactionMapper,
                                                 Account account) {
        return transactionMapper.store.values().stream()
                .filter(transaction -> account.getId().equals(transaction.getAccountId()))
                .map(AccountTransaction::getTranDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
    }

    private static class InMemoryCodefConnectionMapper implements CodefConnectionMapper {

        private final Map<String, CodefConnection> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public void insert(CodefConnection connection) {
            upsert(connection);
        }

        @Override
        public void insertIfAbsent(CodefConnection connection) {
            store.computeIfAbsent(key(connection), ignored -> {
                connection.setId(nextId++);
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
        private long nextId = 1;

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
                    .filter(account -> id.equals(account.getId()) && userId.equals(account.getUserId()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream().filter(account -> userId.equals(account.getUserId())).toList();
        }

        @Override
        public boolean existsAnyByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream().anyMatch(account -> userId.equals(account.getUserId()));
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
            store.values().removeIf(account -> userId.equals(account.getUserId()));
        }

        private static String key(Long connectionId, String accountNoHash) {
            return connectionId + ":" + accountNoHash;
        }
    }

    private static class InMemoryAccountTransactionMapper implements AccountTransactionMapper {

        private final Map<String, AccountTransaction> store = new LinkedHashMap<>();
        private final InMemoryAccountMapper accountMapper;

        private InMemoryAccountTransactionMapper(InMemoryAccountMapper accountMapper) {
            this.accountMapper = accountMapper;
        }

        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            for (AccountTransaction transaction : transactions) {
                String key = transaction.getAccountId() + ":" + transaction.getFingerprint();
                AccountTransaction existing = store.get(key);
                if (existing == null) {
                    store.put(key, transaction);
                } else {
                    existing.setDesc1(existing.getDesc1() != null ? existing.getDesc1() : transaction.getDesc1());
                }
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
            Set<Long> accountIds = accountMapper.store.values().stream()
                    .filter(account -> userId.equals(account.getUserId()))
                    .map(Account::getId)
                    .collect(java.util.stream.Collectors.toSet());
            store.values().removeIf(transaction -> accountIds.contains(transaction.getAccountId()));
        }

        @Override
        public LocalDate findMostRecentTransactionDate(Long codefConnectionId, String organizationCode) {
            return null;
        }
    }
}
