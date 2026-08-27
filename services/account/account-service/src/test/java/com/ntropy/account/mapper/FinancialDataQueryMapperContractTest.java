package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FinancialDataQueryMapperContractTest {

    @Test
    void scopesAccountListAndDetailByUserId() throws IOException {
        String mapper = readMapper();

        assertTrue(mapper.contains("WHERE account_row.user_id = #{userId}"));
        assertTrue(mapper.contains("WHERE account_row.account_id = #{accountId}"));
        assertTrue(mapper.contains("AND account_row.user_id = #{userId}"));
        assertTrue(mapper.contains("AND account_row.status = 'ACTIVE'"));
    }

    @Test
    void verifiesTransactionOwnershipBeforePagedQuery() throws IOException {
        String countQuery = selectBody(readMapper(), "countTransactionsByAccountIdAndUserId");
        String pageQuery = selectBody(readMapper(), "findTransactionsByAccountIdAndUserId");

        assertTrue(countQuery.contains("FROM ACCOUNT account_row"));
        assertTrue(countQuery.contains("LEFT JOIN ACCOUNT_TRANSACTION transaction_row"));
        assertTrue(countQuery.contains("COUNT(transaction_row.account_transaction_id)"));
        assertTrue(countQuery.contains("account_row.account_id = #{accountId}"));
        assertTrue(countQuery.contains("account_row.user_id = #{userId}"));
        assertTrue(pageQuery.contains("INNER JOIN ACCOUNT_TRANSACTION transaction_row"));
        assertTrue(pageQuery.contains("ORDER BY transaction_row.tran_date DESC"));
        assertTrue(pageQuery.contains("LIMIT #{limit} OFFSET #{offset}"));
        assertFalse(pageQuery.contains("EXISTS"));
    }

    @Test
    void classificationTargetFilterCoversOwnershipAndActiveOrdinaryOutflowConditions() throws IOException {
        String filter = sqlFragmentBody(readMapper(), "classificationTargetFilter");

        assertTrue(filter.contains("account_row.user_id = #{userId}"));
        assertTrue(filter.contains("account_row.status = 'ACTIVE'"));
        assertTrue(filter.contains("transaction_row.transaction_category = 'ORDINARY'"));
        assertTrue(filter.contains("transaction_row.out_amount > 0"));
        assertTrue(filter.contains("DATE_FORMAT(transaction_row.tran_date, '%Y-%m') = #{yearMonth}"));
    }

    @Test
    void classificationTargetsAndValidTransactionIdsShareTheSameFilter() throws IOException {
        String targetQuery = selectBody(readMapper(), "findClassificationTargets");
        String validationQuery = selectBody(readMapper(), "findValidTransactionIds");

        assertTrue(targetQuery.contains("<include refid=\"classificationTargetFilter\"/>"));
        assertTrue(validationQuery.contains("<include refid=\"classificationTargetFilter\"/>"));
        assertTrue(validationQuery.contains("transaction_row.account_transaction_id IN"));
    }

    @Test
    void dailyQueryFindsAllSupportedTransactionsWithoutExistingAnalysis()
            throws IOException {

        String query = selectBody(
                readMapper(),
                "findUnanalyzedTransactions"
        );

        assertTrue(query.contains("NOT EXISTS"));
        assertTrue(
                query.contains("FROM TXN_ANALYSIS analysis_row")
        );

        assertTrue(
                query.contains(
                        "transaction_row.transaction_category = 'ORDINARY'"
                )
        );

        assertTrue(
                query.contains(
                        "transaction_row.transaction_category = 'INSTALLMENT'"
                )
        );

        assertTrue(
                query.contains(
                        "transaction_row.transaction_category = 'LOAN'"
                )
        );

        assertTrue(
                query.contains(
                        "transaction_row.out_amount &gt; 0"
                )
        );

        assertTrue(
                query.contains(
                        "transaction_row.in_amount &gt; 0"
                )
        );

        assertTrue(
                query.contains(
                        "ORDER BY transaction_row.account_transaction_id"
                )
        );

        assertTrue(
                query.contains("LIMIT #{limit}")
        );

        /*
         * 과거에 생성된 비활성 계좌 거래도 분석에서 빠지지 않도록
         * ACTIVE 계좌 조건을 사용하지 않습니다.
         */
        assertFalse(
                query.contains("account_row.status = 'ACTIVE'")
        );
    }

    private static String sqlFragmentBody(String mapper, String sqlId) {
        int start = mapper.indexOf("<sql id=\"" + sqlId + "\"");
        int end = mapper.indexOf("</sql>", start);
        return mapper.substring(start, end);
    }

    private static String selectBody(String mapper, String selectId) {
        int start = mapper.indexOf("<select id=\"" + selectId + "\"");
        int end = mapper.indexOf("</select>", start);
        return mapper.substring(start, end);
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/FinancialDataQueryMapper.xml";
        try (InputStream input = FinancialDataQueryMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
