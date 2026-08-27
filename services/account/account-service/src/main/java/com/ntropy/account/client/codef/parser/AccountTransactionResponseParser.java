package com.ntropy.account.client.codef.parser;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.TransactionFingerprint;
import com.ntropy.account.domain.entity.AccountTransaction;

/**
 * CODEF 개인 수시입출 거래내역 조회({@code /v1/kr/bank/p/account/transaction-list}) 응답의
 * {@code data}를 {@link AccountTransaction} 목록으로 변환한다.
 * 공식 문서는 {@code data}를 "계좌별 결과 목록"(배열)이라고 설명하지만, 계좌 하나만 조회하는
 * 실제 DEMO 응답은 배열이 아니라 계좌 요약 객체 하나를 그대로 반환한다. 두 형태 모두 허용한다.
 */
public final class AccountTransactionResponseParser {

    private AccountTransactionResponseParser() {
    }

    public static List<AccountTransaction> parse(JsonNode data, Long accountId) {
        List<AccountTransaction> result = new java.util.ArrayList<>();
        for (JsonNode accountResult : CodefJsonSupport.accountResults(data)) {
            JsonNode historyList = accountResult.path("resTrHistoryList");
            if (!historyList.isArray()) {
                continue;
            }
            for (JsonNode historyNode : historyList) {
                result.add(parseOne(historyNode, accountId));
            }
        }
        return result;
    }

    private static AccountTransaction parseOne(JsonNode node, Long accountId) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setAccountId(accountId);
        transaction.setTransactionCategory(AccountTransactionCategory.ORDINARY);
        transaction.setTranDate(CodefJsonSupport.date(node, "resAccountTrDate"));
        transaction.setTranTime(CodefJsonSupport.time(node, "resAccountTrTime"));

        BigDecimal outAmount = CodefJsonSupport.amount(node, "resAccountOut");
        BigDecimal inAmount = CodefJsonSupport.amount(node, "resAccountIn");
        transaction.setOutAmount(outAmount != null ? outAmount : BigDecimal.ZERO);
        transaction.setInAmount(inAmount != null ? inAmount : BigDecimal.ZERO);
        transaction.setAfterBalance(CodefJsonSupport.amount(node, "resAfterTranBalance"));

        transaction.setDesc1(CodefJsonSupport.text(node, "resAccountDesc1"));
        transaction.setDesc2(CodefJsonSupport.text(node, "resAccountDesc2"));
        transaction.setDesc3(CodefJsonSupport.text(node, "resAccountDesc3"));
        transaction.setDesc4(CodefJsonSupport.text(node, "resAccountDesc4"));
        transaction.setFingerprint(TransactionFingerprint.hash(
                accountId, transaction.getTranDate(), transaction.getTranTime(),
                transaction.getOutAmount(), transaction.getInAmount(), transaction.getAfterBalance(),
                transaction.getDesc2(), transaction.getDesc3(), transaction.getDesc4()
        ));
        return transaction;
    }
}
