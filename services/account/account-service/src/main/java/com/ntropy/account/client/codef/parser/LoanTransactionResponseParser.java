package com.ntropy.account.client.codef.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.TransactionFingerprint;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;

/** CODEF 개인 대출 거래내역 응답을 대출 상세와 원금·이자 거래내역으로 변환한다. */
public final class LoanTransactionResponseParser {

    private LoanTransactionResponseParser() {
    }

    public record ParsedLoan(Account detail, List<AccountTransaction> transactions) {
    }

    public static List<ParsedLoan> parse(JsonNode data, Long accountId) {
        List<ParsedLoan> result = new ArrayList<>();
        for (JsonNode accountResult : CodefJsonSupport.accountResults(data)) {
            result.add(new ParsedLoan(
                    parseDetail(accountResult, accountId),
                    parseTransactions(accountResult, accountId)
            ));
        }
        return result;
    }

    private static Account parseDetail(JsonNode node, Long accountId) {
        Account detail = new Account();
        detail.setId(accountId);
        detail.setAccountStartDate(CodefJsonSupport.date(node, "resAccountStartDate"));
        detail.setMaturityDate(CodefJsonSupport.date(node, "resAccountEndDate"));
        detail.setNextPaymentDate(CodefJsonSupport.date(node, "resDatePayment"));
        // 계좌 상세의 상위 resPrincipal은 약정원금이다. 반복부의 동명 필드(거래 원금)와 경로가 다르다.
        detail.setLoanContractPrincipal(CodefJsonSupport.amount(node, "resPrincipal"));
        // 계좌 상세의 상위 resRate만 계좌 약정이율로 매핑한다. 반복부 resInterestRate(거래 시점 이율)는 사용하지 않는다.
        detail.setInterestRate(CodefJsonSupport.amount(node, "resRate"));
        var loanBalance = CodefJsonSupport.amount(node, "resLoanBalance");
        detail.setBalance(loanBalance != null ? loanBalance.abs() : null);
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
            transaction.setTransactionCategory(AccountTransactionCategory.LOAN);
            transaction.setLoanTransactionTypeName(CodefJsonSupport.text(node, "resTransTypeNm"));
            transaction.setTranDate(CodefJsonSupport.date(node, "resAccountTrDate"));
            transaction.setOutAmount(repaymentAmount(node));
            transaction.setInAmount(BigDecimal.ZERO);
            // 반복부의 resPrincipal/resInterest는 해당 거래의 원금·이자다. 계좌 상세 상위 resPrincipal(약정원금)과 경로가 다르다.
            transaction.setLoanPrincipalAmount(CodefJsonSupport.amount(node, "resPrincipal"));
            transaction.setLoanInterestAmount(CodefJsonSupport.amount(node, "resInterest"));
            transaction.setAfterBalance(CodefJsonSupport.amount(node, "resLoanBalance"));
            transaction.setFingerprint(TransactionFingerprint.hash(
                    accountId, AccountTransactionCategory.LOAN,
                    transaction.getTranDate(), transaction.getOutAmount(), transaction.getAfterBalance()
            ));
            transactions.add(transaction);
        }
        return transactions;
    }

    private static BigDecimal repaymentAmount(JsonNode node) {
        BigDecimal transactionAmount = CodefJsonSupport.amount(node, "resTranAmount");
        if (transactionAmount != null) {
            return transactionAmount.abs();
        }
        BigDecimal principal = CodefJsonSupport.amount(node, "resPrincipal");
        BigDecimal interest = CodefJsonSupport.amount(node, "resInterest");
        return amount(principal).add(amount(interest)).abs();
    }

    private static BigDecimal amount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
