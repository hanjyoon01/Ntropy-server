package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FinancialPositionMapperContractTest {

    @Test
    void scopesAggregationTargetsByOwnerActiveKrwAndDepositTypeCode() throws IOException {
        String query = selectBody(readMapper(), "findFinancialPositionAccounts");

        assertTrue(query.contains("FROM ACCOUNT account_row"));
        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code IN ('11', '12', '40')"));
    }

    @Test
    void selectsRawBalanceAndClassificationColumnsOnly() throws IOException {
        String query = selectBody(readMapper(), "findFinancialPositionAccounts");

        assertTrue(query.contains("account_row.account_id AS accountId"));
        assertTrue(query.contains("account_row.deposit_type_code AS depositTypeCode"));
        assertTrue(query.contains("account_row.account_group AS accountGroup"));
        assertTrue(query.contains("account_row.overdraft_yn AS overdraftYn"));
        assertTrue(query.contains("account_row.balance"));
    }

    @Test
    void asOfQuerySelectsLatestTransactionOnOrBeforeCutoffWithDeterministicTieBreak() throws IOException {
        String query = selectBody(readMapper(), "findFinancialPositionAccountsAsOf");

        assertTrue(query.contains("t.tran_date &lt;= #{asOf}"));
        assertTrue(query.contains("ORDER BY t.tran_date DESC, t.tran_time DESC, t.account_transaction_id DESC"));
        assertTrue(query.contains("LIMIT 1"));
        assertTrue(query.contains("latest_txn.after_balance AS balance"));
        assertTrue(query.contains("latest_txn.after_balance IS NOT NULL"));
    }

    @Test
    void asOfQueryDoesNotFilterByCurrentAccountStatus() throws IOException {
        String query = selectBody(readMapper(), "findFinancialPositionAccountsAsOf");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.deactivated_at IS NULL"));
        assertTrue(query.contains("account_row.deactivated_at &gt;= DATE_ADD(#{asOf}, INTERVAL 1 DAY)"));
    }

    @Test
    void asOfQueryScopesByOwnerCurrencyAndDepositTypeCode() throws IOException {
        String query = selectBody(readMapper(), "findFinancialPositionAccountsAsOf");

        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code IN ('11', '12', '40')"));
    }

    private static String selectBody(String mapper, String selectId) {
        int start = mapper.indexOf("<select id=\"" + selectId + "\"");
        int end = mapper.indexOf("</select>", start);
        return mapper.substring(start, end);
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/FinancialPositionMapper.xml";
        try (InputStream input = FinancialPositionMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
