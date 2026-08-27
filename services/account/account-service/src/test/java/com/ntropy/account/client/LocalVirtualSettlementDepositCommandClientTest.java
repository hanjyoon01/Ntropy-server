package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.VirtualSettlementDepositCommand;
import com.ntropy.account.api.dto.VirtualSettlementDepositResult;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.VirtualSettlementDepositMapper;

class LocalVirtualSettlementDepositCommandClientTest {

    @Test
    void createsIncomeOnNtropyOrdinaryAccountAndUpdatesBalance() {
        StubAccountMapper accountMapper = new StubAccountMapper();
        accountMapper.accounts.add(ordinaryAccount(10L, BigDecimal.valueOf(100_000L)));
        RecordingDepositMapper depositMapper = new RecordingDepositMapper();
        depositMapper.lockedBalance = BigDecimal.valueOf(100_000L);
        LocalVirtualSettlementDepositCommandClient client =
                new LocalVirtualSettlementDepositCommandClient(accountMapper, depositMapper);

        VirtualSettlementDepositResult result = client.createOrAdjust(command(50_000L));

        assertTrue(result.available());
        assertTrue(result.transactionCreated());
        assertEquals(10L, depositMapper.transaction.getAccountId());
        assertEquals(BigDecimal.valueOf(50_000L), depositMapper.transaction.getInAmount());
        assertEquals(BigDecimal.valueOf(150_000L), depositMapper.transaction.getAfterBalance());
        assertEquals("쿠팡이츠정산", depositMapper.transaction.getDesc1());
        assertEquals("쿠팡이츠정산", depositMapper.transaction.getDesc3());
        assertEquals(1, depositMapper.balanceUpdates);
    }

    @Test
    void duplicateFingerprintDoesNotIncreaseBalanceAgain() {
        StubAccountMapper accountMapper = new StubAccountMapper();
        accountMapper.accounts.add(ordinaryAccount(10L, BigDecimal.valueOf(100_000L)));
        RecordingDepositMapper depositMapper = new RecordingDepositMapper();
        depositMapper.lockedBalance = BigDecimal.valueOf(100_000L);
        depositMapper.insertResult = 0;
        LocalVirtualSettlementDepositCommandClient client =
                new LocalVirtualSettlementDepositCommandClient(accountMapper, depositMapper);

        VirtualSettlementDepositResult result = client.createOrAdjust(command(50_000L));

        assertTrue(result.available());
        assertFalse(result.transactionCreated());
        assertEquals(0, depositMapper.balanceUpdates);
    }

    @Test
    void createsOnlyDifferenceWhenSamePeriodTargetAmountIncreases() {
        StubAccountMapper accountMapper = new StubAccountMapper();
        accountMapper.accounts.add(ordinaryAccount(10L, BigDecimal.valueOf(100_000L)));
        RecordingDepositMapper depositMapper = new RecordingDepositMapper();
        depositMapper.lockedBalance = BigDecimal.valueOf(100_000L);
        depositMapper.generatedAmount = BigDecimal.valueOf(50_000L);
        LocalVirtualSettlementDepositCommandClient client =
                new LocalVirtualSettlementDepositCommandClient(accountMapper, depositMapper);

        VirtualSettlementDepositResult result = client.createOrAdjust(command(70_000L));

        assertTrue(result.transactionCreated());
        assertEquals(BigDecimal.valueOf(20_000L), depositMapper.transaction.getInAmount());
        assertEquals(BigDecimal.valueOf(120_000L), depositMapper.transaction.getAfterBalance());
        assertTrue(depositMapper.transaction.getDesc4().startsWith("VS:"));
    }

    @Test
    void alreadyFundedPeriodRemainsAvailableWithoutAnotherTransaction() {
        StubAccountMapper accountMapper = new StubAccountMapper();
        accountMapper.accounts.add(ordinaryAccount(10L, BigDecimal.valueOf(150_000L)));
        RecordingDepositMapper depositMapper = new RecordingDepositMapper();
        depositMapper.lockedBalance = BigDecimal.valueOf(150_000L);
        depositMapper.generatedAmount = BigDecimal.valueOf(50_000L);
        LocalVirtualSettlementDepositCommandClient client =
                new LocalVirtualSettlementDepositCommandClient(accountMapper, depositMapper);

        VirtualSettlementDepositResult result = client.createOrAdjust(command(50_000L));

        assertTrue(result.available());
        assertFalse(result.transactionCreated());
        assertEquals(null, depositMapper.transaction);
        assertEquals(0, depositMapper.balanceUpdates);
    }

    @Test
    void doesNotCreateTransactionWhenVirtualOrdinaryAccountIsMissing() {
        RecordingDepositMapper depositMapper = new RecordingDepositMapper();
        LocalVirtualSettlementDepositCommandClient client =
                new LocalVirtualSettlementDepositCommandClient(new StubAccountMapper(), depositMapper);

        VirtualSettlementDepositResult result = client.createOrAdjust(command(50_000L));

        assertFalse(result.available());
        assertFalse(result.transactionCreated());
        assertEquals(null, depositMapper.transaction);
    }

    private static VirtualSettlementDepositCommand command(long amount) {
        return new VirtualSettlementDepositCommand(
                1L,
                2L,
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 21),
                LocalDate.of(2026, 7, 24),
                amount,
                "쿠팡이츠정산"
        );
    }

    private static Account ordinaryAccount(Long id, BigDecimal balance) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(1L);
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("11");
        account.setBalance(balance);
        return account;
    }

    private static final class StubAccountMapper implements AccountMapper {
        private final List<Account> accounts = new ArrayList<>();

        @Override public void upsert(Account account) { }
        @Override public void upsertAll(List<Account> accountsToUpsert) { }
        @Override public void updateAccountDetails(Account account) { }
        @Override public Account findByConnectionIdAndAccountNoHash(Long connectionId, String hash) { return null; }
        @Override public List<Account> findByConnectionIdAndAccountNoHashes(Long connectionId, List<String> hashes) { return List.of(); }
        @Override public Account findByIdAndUserIdAndProvider(Long id, Long userId, String provider) { return null; }
        @Override public List<Account> findByUserIdAndProvider(Long userId, String provider) { return accounts; }
        @Override public boolean existsAnyByUserIdAndProvider(Long userId, String provider) { return !accounts.isEmpty(); }
        @Override public void deleteByUserIdAndProvider(Long userId, String provider) { }
    }

    private static final class RecordingDepositMapper implements VirtualSettlementDepositMapper {
        private AccountTransaction transaction;
        private int insertResult = 1;
        private int balanceUpdates;
        private BigDecimal lockedBalance = BigDecimal.ZERO;
        private BigDecimal generatedAmount = BigDecimal.ZERO;

        @Override
        public BigDecimal findBalanceForUpdate(Long accountId) {
            return lockedBalance;
        }

        @Override
        public BigDecimal sumGeneratedAmount(Long accountId, String settlementKey) {
            return generatedAmount;
        }

        @Override
        public int insertIfAbsent(AccountTransaction transaction) {
            this.transaction = transaction;
            return insertResult;
        }

        @Override
        public int incrementBalanceAndAdvanceLastTranDate(Long accountId, BigDecimal amount, LocalDate tranDate) {
            balanceUpdates++;
            return 1;
        }
    }
}
