package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.ntropy.account.api.dto.ClassificationTargetTransaction;
import com.ntropy.account.api.dto.DailyClassificationTargetTransaction;
import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialDataQueryMapper;
import com.ntropy.account.mapper.projection.OwnedAccountTransactionRow;
import com.ntropy.account.mapper.projection.OwnedTransactionCountRow;
import com.ntropy.account.api.dto.AccountSummary;
import com.ntropy.account.api.dto.AccountTransactionSummary;
import com.ntropy.common.dto.account.PageSummary;
import com.ntropy.common.exception.ServiceException;

class LocalAccountQueryClientTest {

    @Test
    void listsOnlyAccountsOwnedByRequestedUser() {
        InMemoryFinancialDataQueryMapper mapper = new InMemoryFinancialDataQueryMapper();
        mapper.accounts.add(account(10L, 1L));
        mapper.accounts.add(account(20L, 2L));
        LocalAccountQueryClient client = new LocalAccountQueryClient(mapper);

        List<AccountSummary> result = client.findAccounts(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).accountId());
        assertEquals(1L, mapper.lastUserId);
    }

    @Test
    void returnsAccountOnlyWhenIdAndOwnerMatch() {
        InMemoryFinancialDataQueryMapper mapper = new InMemoryFinancialDataQueryMapper();
        mapper.accounts.add(account(10L, 1L));
        LocalAccountQueryClient client = new LocalAccountQueryClient(mapper);

        AccountSummary result = client.findAccount(1L, 10L);

        assertEquals(10L, result.accountId());
        assertEquals(1L, mapper.lastUserId);
        assertEquals(10L, mapper.lastAccountId);
    }

    @Test
    void doesNotRevealWhetherAccountIsMissingOrOwnedByAnotherUser() {
        InMemoryFinancialDataQueryMapper mapper = new InMemoryFinancialDataQueryMapper();
        mapper.accounts.add(account(10L, 1L));
        LocalAccountQueryClient client = new LocalAccountQueryClient(mapper);

        ServiceException otherOwner = assertThrows(
                ServiceException.class, () -> client.findAccount(2L, 10L)
        );
        ServiceException missing = assertThrows(
                ServiceException.class, () -> client.findAccount(2L, 999L)
        );

        assertSameNotFoundResponse(otherOwner, missing);
    }

    @Test
    void returnsTransactionsOnlyForOwnedAccount() {
        InMemoryFinancialDataQueryMapper mapper = new InMemoryFinancialDataQueryMapper();
        mapper.accounts.add(account(10L, 1L));
        mapper.transactions.add(transaction(101L, 10L));
        LocalAccountQueryClient client = new LocalAccountQueryClient(mapper);

        PageSummary<AccountTransactionSummary> result =
                client.findTransactions(1L, 10L, null, null, 0, 30);

        assertEquals(1, result.content().size());
        assertEquals(101L, result.content().get(0).transactionId());
        assertEquals(10L, result.content().get(0).accountId());
        assertEquals(1, result.totalElements());
        assertEquals(1L, mapper.lastUserId);
        assertEquals(10L, mapper.lastAccountId);
    }

    @Test
    void returnsEmptyTransactionsForOwnedAccountWithoutRunningOwnershipQuery() {
        InMemoryFinancialDataQueryMapper mapper = new InMemoryFinancialDataQueryMapper();
        mapper.accounts.add(account(10L, 1L));
        LocalAccountQueryClient client = new LocalAccountQueryClient(mapper);

        PageSummary<AccountTransactionSummary> result =
                client.findTransactions(1L, 10L, null, null, 0, 30);

        assertEquals(List.of(), result.content());
        assertEquals(0, mapper.accountDetailQueryCalls);
        assertEquals(0, mapper.transactionQueryCalls);
    }

    @Test
    void blocksOtherUsersTransactionsWithSameResponseAsMissingAccount() {
        InMemoryFinancialDataQueryMapper mapper = new InMemoryFinancialDataQueryMapper();
        mapper.accounts.add(account(10L, 1L));
        mapper.transactions.add(transaction(101L, 10L));
        LocalAccountQueryClient client = new LocalAccountQueryClient(mapper);

        ServiceException otherOwner = assertThrows(
                ServiceException.class, () -> client.findTransactions(2L, 10L, null, null, 0, 30)
        );
        ServiceException missing = assertThrows(
                ServiceException.class, () -> client.findTransactions(2L, 999L, null, null, 0, 30)
        );

        assertSameNotFoundResponse(otherOwner, missing);
        assertEquals(0, mapper.accountDetailQueryCalls);
        assertEquals(2, mapper.transactionCountQueryCalls);
    }

    @Test
    void labelsConnectionTypeCorrectlyWhenUserHoldsBothCodefAndNtropyAccounts() {
        InMemoryFinancialDataQueryMapper mapper = new InMemoryFinancialDataQueryMapper();
        Account codefAccount = account(10L, 1L);
        codefAccount.setConnectionProvider("CODEF");
        Account ntropyAccount = account(20L, 1L);
        ntropyAccount.setConnectionProvider("NTROPY");
        mapper.accounts.add(codefAccount);
        mapper.accounts.add(ntropyAccount);
        LocalAccountQueryClient client = new LocalAccountQueryClient(mapper);

        List<AccountSummary> result = client.findAccounts(1L);

        assertEquals(2, result.size());
        assertEquals("CODEF", result.stream()
                .filter(summary -> summary.accountId().equals(10L)).findFirst().orElseThrow().connectionType());
        assertEquals("VIRTUAL", result.stream()
                .filter(summary -> summary.accountId().equals(20L)).findFirst().orElseThrow().connectionType());
    }

    private static void assertSameNotFoundResponse(ServiceException first, ServiceException second) {
        assertEquals(AccountErrorCode.ACCOUNT_NOT_FOUND.getStatusCode(), first.getStatusCode());
        assertEquals(first.getStatusCode(), second.getStatusCode());
        assertEquals(first.getErrorMessage(), second.getErrorMessage());
    }

    private static Account account(Long accountId, Long userId) {
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(userId);
        account.setOrganizationCode("0004");
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("11");
        account.setAccountNoMasked("****1234");
        account.setAccountName("생활비 계좌");
        account.setBalance(new BigDecimal("120000"));
        account.setCurrencyCode("KRW");
        return account;
    }

    private static OwnedAccountTransactionRow transaction(Long transactionId, Long accountId) {
        OwnedAccountTransactionRow row = new OwnedAccountTransactionRow();
        row.setOwnedAccountId(accountId);
        row.setTransactionId(transactionId);
        row.setTransactionCategory(AccountTransactionCategory.ORDINARY);
        row.setTransactionDate(LocalDate.of(2026, 8, 1));
        row.setTransactionTime(LocalTime.of(9, 30));
        row.setOutAmount(new BigDecimal("30000"));
        row.setInAmount(BigDecimal.ZERO);
        row.setAfterBalance(new BigDecimal("120000"));
        return row;
    }

    private static class InMemoryFinancialDataQueryMapper implements FinancialDataQueryMapper {

        private final List<Account> accounts = new ArrayList<>();
        private final List<OwnedAccountTransactionRow> transactions = new ArrayList<>();
        private Long lastUserId;
        private Long lastAccountId;
        private int accountDetailQueryCalls;
        private int transactionQueryCalls;
        private int transactionCountQueryCalls;

        @Override
        public List<Account> findAccountsByUserId(Long userId) {
            lastUserId = userId;
            return accounts.stream().filter(account -> userId.equals(account.getUserId())).toList();
        }

        @Override
        public Account findAccountByIdAndUserId(Long accountId, Long userId) {
            accountDetailQueryCalls++;
            lastAccountId = accountId;
            lastUserId = userId;
            return accounts.stream()
                    .filter(account -> accountId.equals(account.getId()) && userId.equals(account.getUserId()))
                    .findFirst().orElse(null);
        }

        @Override
        public OwnedTransactionCountRow countTransactionsByAccountIdAndUserId(
                Long accountId, Long userId, LocalDate startDate, LocalDate endDate
        ) {
            transactionCountQueryCalls++;
            lastAccountId = accountId;
            lastUserId = userId;
            boolean owned = accounts.stream()
                    .anyMatch(account -> accountId.equals(account.getId()) && userId.equals(account.getUserId()));
            if (!owned) {
                return null;
            }
            long count = transactions.stream()
                    .filter(row -> accountId.equals(row.getOwnedAccountId()))
                    .count();
            OwnedTransactionCountRow row = new OwnedTransactionCountRow();
            row.setOwnedAccountId(accountId);
            row.setTotalElements(count);
            return row;
        }

        @Override
        public List<OwnedAccountTransactionRow> findTransactionsByAccountIdAndUserId(
                Long accountId, Long userId, LocalDate startDate, LocalDate endDate, int limit, int offset
        ) {
            transactionQueryCalls++;
            List<OwnedAccountTransactionRow> rows = transactions.stream()
                    .filter(row -> accountId.equals(row.getOwnedAccountId()))
                    .toList();
            int from = Math.min(offset, rows.size());
            int to = Math.min(from + limit, rows.size());
            return rows.subList(from, to);
        }

        @Override
        public List<ClassificationTargetTransaction> findClassificationTargets(
                Long userId,
                String yearMonth
        ) {
            return List.of();
        }

        @Override
        public List<Long> findValidTransactionIds(
                Long userId,
                String yearMonth,
                List<Long> transactionIds
        ) {
            return List.of();
        }

        @Override
        public List<DailyClassificationTargetTransaction>
        findUnanalyzedTransactions(int limit) {
            return List.of();
        }

        @Override
        public List<DailyClassificationTargetTransaction>
        findUnanalyzedTransactionsByUserId(Long userId, int limit) {
            return List.of();
        }
    }
}
