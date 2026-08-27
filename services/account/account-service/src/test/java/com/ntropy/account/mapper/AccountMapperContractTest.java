package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class AccountMapperContractTest {

    @Test
    void bulkUpsertKeepsSingleUpsertColumnsAndDuplicateUpdatePolicy() throws IOException {
        String mapper = readResource("mapper/account/AccountMapper.xml");
        String single = statement(mapper, "insert", "upsert");
        String bulk = statement(mapper, "insert", "upsertAll");

        assertEquals(insertColumns(single), insertColumns(bulk),
                "단건/bulk upsert의 INSERT 컬럼은 같아야 합니다");
        assertEquals(duplicateUpdateClause(single), duplicateUpdateClause(bulk),
                "단건/bulk upsert의 ON DUPLICATE KEY 갱신 정책은 같아야 합니다");
        assertTrue(bulk.contains("<foreach collection=\"list\" item=\"account\" separator=\",\">"));
    }

    @Test
    void bulkLookupKeepsSingleLookupProjectionAndUsesCompositeKeyPredicate() throws IOException {
        String mapper = readResource("mapper/account/AccountMapper.xml");
        String single = statement(mapper, "select", "findByConnectionIdAndAccountNoHash");
        String bulk = statement(mapper, "select", "findByConnectionIdAndAccountNoHashes");

        assertEquals(selectProjection(single), selectProjection(bulk),
                "단건/bulk 조회가 반환하는 Account 필드는 같아야 합니다");
        assertTrue(bulk.contains("codef_connection_id = #{codefConnectionId}"));
        assertTrue(bulk.contains("account_no_hash IN"));
        assertTrue(bulk.contains("<foreach collection=\"accountNoHashes\" item=\"hash\""));
    }

    @Test
    void virtualDatasetExistenceCheckIncludesInactiveAccounts() throws IOException {
        String mapper = readResource("mapper/account/AccountMapper.xml");
        String statementStart = "<select id=\"existsAnyByUserIdAndProvider\"";
        int start = mapper.indexOf(statementStart);
        assertTrue(start >= 0, "가상계좌 존재 확인 쿼리가 필요합니다");

        int end = mapper.indexOf("</select>", start);
        assertTrue(end > start, "가상계좌 존재 확인 쿼리가 닫혀 있어야 합니다");
        String statement = mapper.substring(start, end).toLowerCase(Locale.ROOT);

        assertTrue(statement.contains("select exists"));
        assertTrue(statement.contains("connection_row.provider = #{provider}"));
        assertFalse(statement.contains("status"), "비활성 계좌도 기존 가상 데이터셋으로 판단해야 합니다");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = AccountMapperContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + "가 테스트 classpath에 있어야 합니다");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static String statement(String mapper, String tag, String id) {
        String startToken = "<" + tag + " id=\"" + id + "\"";
        int start = mapper.indexOf(startToken);
        assertTrue(start >= 0, id + " statement가 필요합니다");
        int bodyStart = mapper.indexOf('>', start) + 1;
        int end = mapper.indexOf("</" + tag + ">", bodyStart);
        assertTrue(end > bodyStart, id + " statement가 닫혀 있어야 합니다");
        return mapper.substring(bodyStart, end);
    }

    private static String insertColumns(String statement) {
        int start = statement.indexOf('(') + 1;
        int end = statement.indexOf(") VALUES", start);
        return normalize(statement.substring(start, end));
    }

    private static String duplicateUpdateClause(String statement) {
        int start = statement.indexOf("ON DUPLICATE KEY UPDATE");
        return normalize(statement.substring(start));
    }

    private static String selectProjection(String statement) {
        int start = statement.indexOf("SELECT") + "SELECT".length();
        int end = statement.indexOf("FROM ACCOUNT", start);
        return normalize(statement.substring(start, end));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
