package com.ntropy.account.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;

/** 거래별 잔액 흐름과 계좌 최종 잔액의 정합성을 검사한다. */
public final class AccountBalanceConsistencyValidator {

    private AccountBalanceConsistencyValidator() {
    }

    public static void validate(Account account, List<AccountTransaction> source) {
        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException("검증할 계좌가 필요합니다");
        }
        if (source == null || source.isEmpty()) {
            throw new IllegalStateException("잔액을 검증할 거래가 없습니다: accountId=" + account.getId());
        }

        List<AccountTransaction> transactions = new ArrayList<>(source);
        transactions.sort(Comparator
                .comparing(AccountTransaction::getTranDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AccountTransaction::getTranTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(transaction -> transaction.getId() == null ? Long.MAX_VALUE : transaction.getId()));

        AccountTransaction first = transactions.get(0);
        BigDecimal runningBalance = amount(first.getAfterBalance())
                .subtract(amount(first.getInAmount()))
                .add(amount(first.getOutAmount()));

        for (AccountTransaction transaction : transactions) {
            BigDecimal expected = runningBalance
                    .add(amount(transaction.getInAmount()))
                    .subtract(amount(transaction.getOutAmount()));
            BigDecimal actual = amount(transaction.getAfterBalance());
            if (expected.compareTo(actual) != 0) {
                throw new IllegalStateException(
                        "거래 후 잔액 불일치: accountId=" + account.getId()
                                + ", fingerprint=" + transaction.getFingerprint()
                                + ", expected=" + expected.toPlainString()
                                + ", actual=" + actual.toPlainString()
                );
            }
            if (actual.signum() < 0) {
                throw new IllegalStateException(
                        "거래 후 잔액이 음수입니다: accountId=" + account.getId()
                                + ", fingerprint=" + transaction.getFingerprint()
                );
            }
            runningBalance = actual;
        }

        BigDecimal accountBalance = amount(account.getBalance());
        if (runningBalance.compareTo(accountBalance) != 0) {
            throw new IllegalStateException(
                    "계좌 최종 잔액 불일치: accountId=" + account.getId()
                            + ", expected=" + runningBalance.toPlainString()
                            + ", actual=" + accountBalance.toPlainString()
            );
        }
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
