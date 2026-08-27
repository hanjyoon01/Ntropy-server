package com.ntropy.account.client.codef.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.TransactionFingerprint;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;

/** CODEF 개인 적금 거래내역 응답을 계좌 상세와 납입내역으로 변환한다. */
public final class InstallmentSavingsResponseParser {

    private InstallmentSavingsResponseParser() {
    }

    public record ParsedInstallmentSavings(Account detail, List<AccountTransaction> transactions) {
    }

    public static List<ParsedInstallmentSavings> parse(JsonNode data, Long accountId) {
        List<ParsedInstallmentSavings> result = new ArrayList<>();
        for (JsonNode accountResult : CodefJsonSupport.accountResults(data)) {
            List<AccountTransaction> transactions = parseTransactions(accountResult, accountId);
            result.add(new ParsedInstallmentSavings(parseDetail(accountResult, accountId), transactions));
        }
        return result;
    }

    private static Account parseDetail(JsonNode node, Long accountId) {
        Account detail = new Account();
        detail.setId(accountId);
        detail.setInterestRate(CodefJsonSupport.amount(node, "resRate"));
        detail.setMaturityDate(CodefJsonSupport.date(node, "resAccountEndDate"));
        // CODEF 적금 거래내역에는 다음 납입일 필드가 없다. 수집 서비스가 기본 일정(다음 25일)을 적용한다.
        return detail;
    }

    private static List<AccountTransaction> parseTransactions(JsonNode accountResult, Long accountId) {
        JsonNode historyList = accountResult.path("resTrHistoryList");
        if (!historyList.isArray()) {
            return List.of();
        }

        List<AccountTransaction> transactions = new ArrayList<>();
        for (JsonNode node : historyList) {
            AccountTransaction transaction = new AccountTransaction();
            transaction.setAccountId(accountId);
            transaction.setTransactionCategory(AccountTransactionCategory.INSTALLMENT);
            transaction.setTranDate(CodefJsonSupport.date(node, "resAccountTrDate"));
            BigDecimal inAmount = CodefJsonSupport.amount(node, "resAccountIn");
            transaction.setInAmount(inAmount != null ? inAmount : BigDecimal.ZERO);
            transaction.setDesc2(CodefJsonSupport.text(node, "resAccountDesc2"));
            transaction.setDesc3(CodefJsonSupport.text(node, "resAccountDesc3"));
            transaction.setDesc4(CodefJsonSupport.text(node, "resAccountDesc4"));
            transaction.setAfterBalance(CodefJsonSupport.amount(node, "resAfterTranBalance"));
            transaction.setFingerprint(TransactionFingerprint.hash(
                    accountId,
                    AccountTransactionCategory.INSTALLMENT,
                    transaction.getTranDate(),
                    transaction.getInAmount(), transaction.getAfterBalance(),
                    transaction.getDesc2(), transaction.getDesc3(), transaction.getDesc4()
            ));
            transactions.add(transaction);
        }
        return transactions;
    }
}
