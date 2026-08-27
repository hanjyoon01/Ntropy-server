package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class IncomingTransactionQueryMapperContractTest {

    @Test
    void queriesOnlyOrdinaryIncomingTransactionsForUserAndDateRange() throws IOException {
        String mapper = readResource("mapper/account/IncomingTransactionQueryMapper.xml");

        assertTrue(mapper.contains("account_row.user_id = #{userId}"));
        assertTrue(mapper.contains("transaction_row.transaction_category = 'ORDINARY'"));
        assertTrue(mapper.contains("transaction_row.in_amount &gt; 0"));
        assertTrue(mapper.contains("transaction_row.tran_date BETWEEN #{startDate} AND #{endDate}"));
        assertTrue(mapper.contains("transaction_row.account_transaction_id AS transactionId"));
        assertTrue(mapper.contains("account_row.organization_code"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = IncomingTransactionQueryMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
