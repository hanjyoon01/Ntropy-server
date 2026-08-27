package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;

class AccountBalanceConsistencyValidatorTest {

    @Test
    void acceptsContinuousTransactionAndAccountBalances() {
        Account account = account("1300");
        List<AccountTransaction> transactions = List.of(
                transaction(1L, "1000", "0", "1000"),
                transaction(2L, "500", "200", "1300")
        );

        assertDoesNotThrow(() -> AccountBalanceConsistencyValidator.validate(account, transactions));
    }

    @Test
    void rejectsBrokenTransactionBalance() {
        Account account = account("1400");
        List<AccountTransaction> transactions = List.of(
                transaction(1L, "1000", "0", "1000"),
                transaction(2L, "500", "200", "1400")
        );

        assertThrows(
                IllegalStateException.class,
                () -> AccountBalanceConsistencyValidator.validate(account, transactions)
        );
    }

    @Test
    void rejectsDifferentFinalAccountBalance() {
        Account account = account("9999");
        List<AccountTransaction> transactions = List.of(
                transaction(1L, "1000", "0", "1000"),
                transaction(2L, "500", "200", "1300")
        );

        assertThrows(
                IllegalStateException.class,
                () -> AccountBalanceConsistencyValidator.validate(account, transactions)
        );
    }

    private static Account account(String balance) {
        Account account = new Account();
        account.setId(1L);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    private static AccountTransaction transaction(Long id, String inAmount,
                                                  String outAmount, String afterBalance) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setId(id);
        transaction.setAccountId(1L);
        transaction.setFingerprint("fingerprint-" + id);
        transaction.setTranDate(LocalDate.of(2026, 4, id.intValue()));
        transaction.setTranTime(LocalTime.NOON);
        transaction.setInAmount(new BigDecimal(inAmount));
        transaction.setOutAmount(new BigDecimal(outAmount));
        transaction.setAfterBalance(new BigDecimal(afterBalance));
        return transaction;
    }
}
