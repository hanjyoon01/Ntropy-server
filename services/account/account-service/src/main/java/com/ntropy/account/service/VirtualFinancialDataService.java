package com.ntropy.account.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.domain.AccountBalanceConsistencyValidator;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.AccountNoMask;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator.GeneratedTransactions;
import com.ntropy.account.port.user.SeededVirtualUser;
import com.ntropy.account.port.user.SeededVirtualUserBatch;
import com.ntropy.account.port.user.UserPort;
import com.ntropy.account.port.user.VirtualDatasetExecutionContext;

import lombok.RequiredArgsConstructor;

/** account-service 테이블에 FIN-005 가상 금융 데이터셋을 멱등하게 저장한다. */
@Service
@RequiredArgsConstructor
public class VirtualFinancialDataService {

    public static final int USER_COUNT = 50;
    public static final int ACCOUNTS_PER_USER = 3;
    public static final int EXPECTED_TRANSACTION_COUNT = 15_300;

    private static final String DATASET_VERSION = "FIN-005-v1";
    private static final String CURRENCY_KRW = "KRW";
    private static final String VIRTUAL_PROVIDER = ConnectionProvider.NTROPY.name();
    private static final int TRANSACTION_BATCH_SIZE = 500;

    private final VirtualConnectionService virtualConnectionService;
    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final VirtualFinancialTransactionGenerator transactionGenerator;
    private final Clock clock;
    private final UserPort userPort;

    /** user-service가 시딩한 실제 가상회원 목록을 조회해 이 서비스의 생성기를 호출하는 조합 지점. */
    @Transactional
    public GenerationSummary generate() {
        SeededVirtualUserBatch dataset = userPort.findSeededVirtualUsers();
        return generateForUsers(dataset.users(), dataset.context());
    }

    /** 실제 USERS.user_id 목록과 실행 컨텍스트를 받아 가상 금융 데이터셋을 멱등하게 생성한다. */
    @Transactional
    public GenerationSummary generateForUsers(List<SeededVirtualUser> users, VirtualDatasetExecutionContext context) {
        if (users == null || users.isEmpty()) {
            throw new IllegalArgumentException("가상회원 목록이 필요합니다");
        }
        if (context == null) {
            throw new IllegalArgumentException("데이터셋 실행 컨텍스트가 필요합니다");
        }
        LocalDate referenceDate = context.referenceDate();
        int totalUsers = users.size();
        int generatedAccounts = 0;
        int generatedTransactions = 0;
        PersonalBank[] banks = PersonalBank.values();

        for (SeededVirtualUser identity : users) {
            int userOrdinal = identity.ordinal();
            PersonalBank bank = bankFor(userOrdinal, context.randomSeed(), banks);
            UserGenerationResult result = generateAccountsAndTransactions(
                    identity.userId(), bank, userOrdinal, referenceDate,
                    (ordinaryAccount, installmentAccount, loanAccount) -> transactionGenerator.generate(
                            referenceDate, userOrdinal, context.randomSeed(), bank,
                            ordinaryAccount, installmentAccount, loanAccount
                    )
            );
            generatedAccounts += result.accounts();
            generatedTransactions += result.transactions();
        }

        // 지난 2개월은 사용자당 204건 고정이고, 현재 월은 기준일까지만 생성돼 사용자당 0~102건이다.
        int minimumExpected = totalUsers * VirtualFinancialTransactionGenerator.TRANSACTIONS_PER_USER_PER_MONTH * 2;
        int maximumExpected = totalUsers * VirtualFinancialTransactionGenerator.TRANSACTIONS_PER_USER_PER_MONTH * 3;
        if (generatedTransactions < minimumExpected || generatedTransactions > maximumExpected) {
            throw new IllegalStateException(
                    "가상 거래 총 건수 불일치: expected=[" + minimumExpected + "," + maximumExpected + "]"
                            + ", actual=" + generatedTransactions
            );
        }
        return new GenerationSummary(
                totalUsers, generatedAccounts, VirtualFinancialTransactionGenerator.INCOME_COUNTERPARTY_COUNT,
                generatedTransactions,
                YearMonth.from(referenceDate).minusMonths(2).atDay(1),
                referenceDate
        );
    }

    /** 로그인 사용자 한 명에게 선택 은행의 가상계좌 3개(수시입출금·적금·대출)와 목 거래를 멱등 생성한다. */
    @Transactional
    public GenerationSummary generateForUser(Long userId, PersonalBank bank) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다");
        }
        if (bank == null) {
            throw new IllegalArgumentException("은행이 필요합니다");
        }
        LocalDate referenceDate = LocalDate.now(clock);
        int ordinal = stableUserOrdinal(userId);
        UserGenerationResult result = generateAccountsAndTransactions(
                userId, bank, ordinal, referenceDate,
                (ordinaryAccount, installmentAccount, loanAccount) -> transactionGenerator.generateForUser(
                        referenceDate, userId, bank, ordinaryAccount, installmentAccount, loanAccount
                )
        );
        return new GenerationSummary(
                1, result.accounts(), result.incomeCounterparties(), result.transactions(),
                YearMonth.from(referenceDate).minusMonths(2).atDay(1),
                referenceDate
        );
    }

    private UserGenerationResult generateAccountsAndTransactions(
            Long userId, PersonalBank bank, int userOrdinal, LocalDate referenceDate,
            TransactionsSupplier transactionsSupplier) {
        CodefConnection connection = virtualConnectionService.getOrCreateConnection(userId);
        virtualConnectionService.registerInstitution(connection, bank.getOrganizationCode());

        Account ordinaryAccount = buildOrdinaryAccount(userOrdinal, userId, connection, bank, referenceDate);
        Account installmentAccount = buildInstallmentAccount(userOrdinal, userId, connection, bank, referenceDate);
        Account loanAccount = buildLoanAccount(userOrdinal, userId, connection, bank, referenceDate);
        List<Account> expectedAccounts = List.of(ordinaryAccount, installmentAccount, loanAccount);
        reconcileLegacyAccountLayout(userId, expectedAccounts);

        ordinaryAccount = saveAccount(ordinaryAccount);
        installmentAccount = saveAccount(installmentAccount);
        loanAccount = saveAccount(loanAccount);
        GeneratedTransactions generated = transactionsSupplier.apply(ordinaryAccount, installmentAccount, loanAccount);

        ordinaryAccount.setBalance(generated.finalBalances().get(ordinaryAccount.getId()));
        installmentAccount.setBalance(generated.finalBalances().get(installmentAccount.getId()));
        loanAccount.setBalance(generated.finalBalances().get(loanAccount.getId()));
        ordinaryAccount.setLastTranDate(lastTransactionDate(ordinaryAccount.getId(), generated.transactions()));
        installmentAccount.setLastTranDate(lastTransactionDate(installmentAccount.getId(), generated.transactions()));
        loanAccount.setLastTranDate(lastTransactionDate(loanAccount.getId(), generated.transactions()));
        accountMapper.upsert(ordinaryAccount);
        accountMapper.upsert(installmentAccount);
        accountMapper.upsert(loanAccount);

        insertInBatches(generated.transactions());
        validateStoredBalance(ordinaryAccount, referenceDate);
        validateStoredBalance(installmentAccount, referenceDate);
        validateStoredBalance(loanAccount, referenceDate);
        return new UserGenerationResult(3, generated.transactions().size(), generated.userIncomeCounterpartyCount());
    }

    /**
     * 2계좌 시절의 accountOrdinal=2 계좌와 거래가 남아 있으면 새 거래의 잔액 흐름과 섞인다.
     * 새 3계좌 레이아웃과 다른 경우에만 해당 사용자의 NTROPY 계좌·거래를 정리해 한 번만 마이그레이션한다.
     */
    private void reconcileLegacyAccountLayout(Long userId, List<Account> expectedAccounts) {
        List<Account> existingAccounts = accountMapper.findByUserIdAndProvider(userId, VIRTUAL_PROVIDER);
        if (existingAccounts == null || existingAccounts.isEmpty()) {
            return;
        }

        boolean currentLayout = existingAccounts.size() == expectedAccounts.size()
                && expectedAccounts.stream().allMatch(expected -> existingAccounts.stream()
                .anyMatch(existing -> sameAccountLayout(existing, expected)));
        if (currentLayout) {
            return;
        }

        accountTransactionMapper.deleteByUserIdAndProvider(userId, VIRTUAL_PROVIDER);
        accountMapper.deleteByUserIdAndProvider(userId, VIRTUAL_PROVIDER);
    }

    private static boolean sameAccountLayout(Account existing, Account expected) {
        return Objects.equals(existing.getCodefConnectionId(), expected.getCodefConnectionId())
                && Objects.equals(existing.getAccountNoHash(), expected.getAccountNoHash())
                && existing.getAccountGroup() == expected.getAccountGroup()
                && Objects.equals(existing.getDepositTypeCode(), expected.getDepositTypeCode());
    }

    @FunctionalInterface
    private interface TransactionsSupplier {
        GeneratedTransactions apply(Account ordinaryAccount, Account installmentAccount, Account loanAccount);
    }

    /** 은행 배정: 순번을 randomSeed만큼 순환시켜, 데이터셋 버전(랜덤시드)이 바뀌면 배정도 함께 바뀌도록 한다. */
    private static PersonalBank bankFor(int userOrdinal, long randomSeed, PersonalBank[] banks) {
        int index = (int) Math.floorMod((userOrdinal - 1L) + randomSeed, (long) banks.length);
        return banks[index];
    }

    private static int stableUserOrdinal(Long userId) {
        int profileCount = VirtualFinancialTransactionGenerator.ConsumerProfile.values().length;
        int hash = Long.hashCode(userId);
        int profileIndex = Math.floorMod(hash, profileCount);
        int variation = Math.floorMod(hash / profileCount, 12);
        return 1 + profileIndex + profileCount * variation;
    }

    private Account saveAccount(Account account) {
        accountMapper.upsert(account);
        Account saved = accountMapper.findByConnectionIdAndAccountNoHash(
                account.getCodefConnectionId(), account.getAccountNoHash()
        );
        if (saved == null || saved.getId() == null) {
            throw new IllegalStateException("가상 금융계좌 저장 확인 실패");
        }
        return saved;
    }

    private static Account buildOrdinaryAccount(int userOrdinal, Long userId,
                                                CodefConnection connection, PersonalBank bank,
                                                LocalDate referenceDate) {
        Account account = baseAccount(userOrdinal, userId, connection, bank, 1, referenceDate);
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("11");
        account.setAccountName(bank.getDisplayName() + " 가상 수시입출금");
        account.setBalance(BigDecimal.ZERO);
        account.setOverdraftYn(false);
        // 입출금계좌는 이율이 적용되지 않아 interestRate=null로 둔다.
        return account;
    }

    private static Account buildInstallmentAccount(int userOrdinal, Long userId,
                                                    CodefConnection connection, PersonalBank bank,
                                                    LocalDate referenceDate) {
        Account account = baseAccount(userOrdinal, userId, connection, bank, 2, referenceDate);
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("12");
        account.setAccountName("청년희망적금");
        account.setBalance(BigDecimal.ZERO);
        account.setOverdraftYn(null);
        account.setNextPaymentDate(DefaultPaymentSchedule.nextAfter(referenceDate));
        account.setInterestRate(installmentInterestRate(userOrdinal));
        account.setMaturityDate(referenceDate.plusYears(2));
        return account;
    }

    private static Account buildLoanAccount(int userOrdinal, Long userId,
                                            CodefConnection connection, PersonalBank bank,
                                            LocalDate referenceDate) {
        Account account = baseAccount(userOrdinal, userId, connection, bank, 3, referenceDate);
        account.setAccountGroup(AccountGroup.LOAN);
        account.setDepositTypeCode("40");
        account.setAccountName("주택담보대출");
        account.setBalance(BigDecimal.ZERO);
        account.setOverdraftYn(null);
        account.setNextPaymentDate(DefaultPaymentSchedule.nextAfter(referenceDate));
        account.setLoanContractPrincipal(loanContractPrincipal(userOrdinal));
        account.setInterestRate(loanInterestRate(userOrdinal));
        account.setMaturityDate(referenceDate.plusYears(10));
        return account;
    }

    private static Account baseAccount(int userOrdinal, Long userId,
                                       CodefConnection connection, PersonalBank bank, int accountOrdinal,
                                       LocalDate referenceDate) {
        String rawAccountNo = String.format(Locale.ROOT, "46%03d%07d", userOrdinal, accountOrdinal);
        Account account = new Account();
        account.setCodefConnectionId(connection.getId());
        account.setUserId(userId);
        account.setOrganizationCode(bank.getOrganizationCode());
        account.setAccountNoMasked(AccountNoMask.mask(rawAccountNo));
        account.setAccountNoHash(AccountNoHash.hash(
                bank.getOrganizationCode(), DATASET_VERSION + ":" + rawAccountNo
        ));
        account.setCurrencyCode(CURRENCY_KRW);
        account.setAccountStartDate(LocalDate.of(2025, 1, 1).plusDays(userOrdinal));
        return account;
    }

    private static LocalDate lastTransactionDate(Long accountId, List<AccountTransaction> transactions) {
        return transactions.stream()
                .filter(transaction -> accountId.equals(transaction.getAccountId()))
                .map(AccountTransaction::getTranDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    /** 적금 고정이율: 2.00~3.20% 사이에서 사용자 순번에 따라 결정적으로 배정한다. */
    private static BigDecimal installmentInterestRate(int userOrdinal) {
        return BigDecimal.valueOf(200 + (userOrdinal % 5) * 30, 2);
    }

    /** 대출 고정이율: 3.00~4.60% 사이에서 사용자 순번에 따라 결정적으로 배정한다. */
    private static BigDecimal loanInterestRate(int userOrdinal) {
        return BigDecimal.valueOf(300 + (userOrdinal % 5) * 40, 2);
    }

    private static BigDecimal loanContractPrincipal(int userOrdinal) {
        return BigDecimal.valueOf(50_000_000L + (userOrdinal % 10) * 5_000_000L);
    }

    private void insertInBatches(List<AccountTransaction> transactions) {
        for (int start = 0; start < transactions.size(); start += TRANSACTION_BATCH_SIZE) {
            int end = Math.min(start + TRANSACTION_BATCH_SIZE, transactions.size());
            accountTransactionMapper.insertAll(transactions.subList(start, end));
        }
    }

    private void validateStoredBalance(Account account, LocalDate referenceDate) {
        List<AccountTransaction> transactions = accountTransactionMapper.findByAccountIdAndDateRange(
                account.getId(),
                YearMonth.from(referenceDate).minusMonths(2).atDay(1),
                referenceDate
        );
        AccountBalanceConsistencyValidator.validate(account, transactions);
    }

    public record GenerationSummary(
            int users,
            int accounts,
            int incomeCounterparties,
            int transactions,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    private record UserGenerationResult(int accounts, int transactions, int incomeCounterparties) {
    }
}
