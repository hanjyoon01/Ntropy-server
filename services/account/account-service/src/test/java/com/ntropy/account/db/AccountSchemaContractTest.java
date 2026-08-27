package com.ntropy.account.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.domain.entity.CodefToken;

class AccountSchemaContractTest {

    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "(?im)^\\s*`?([a-z][a-z0-9_]*)`?\\s+"
                    + "(?:BIGINT|VARCHAR|CHAR|JSON|DATETIME|DECIMAL|DATE|TIME|BOOLEAN)\\b"
    );

    private static String schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        schema = readResource("db/account-service-schema.sql");
    }

    @Test
    void schemaColumnsMatchCurrentEntities() {
        assertEntityColumns("CODEF_CONNECTION", CodefConnection.class);
        assertEntityColumns("CODEF_TOKEN", CodefToken.class);
        assertEntityColumns("ACCOUNT", Account.class);
        assertEntityColumns("ACCOUNT_TRANSACTION", AccountTransaction.class);
    }

    @Test
    void mappersCoverEverySchemaColumn() throws IOException {
        assertMapperCoversColumns("CODEF_CONNECTION", "mapper/account/CodefConnectionMapper.xml");
        assertMapperCoversColumns("CODEF_TOKEN", "mapper/account/CodefTokenMapper.xml");
        assertMapperCoversColumns("ACCOUNT", "mapper/account/AccountMapper.xml");
        assertMapperCoversColumns("ACCOUNT_TRANSACTION", "mapper/account/AccountTransactionMapper.xml");
    }

    @Test
    void schemaDeclaresFinalKeysAndIndexes() {
        assertTrue(schema.contains("UNIQUE KEY uk_codef_connection_user_provider (user_id, provider)"));
        assertTrue(schema.contains(
                "INDEX ix_codef_token_lookup (service_type, client_id, codef_token_id)"));
        assertTrue(schema.contains(
                "UNIQUE KEY uk_account_connection_hash (codef_connection_id, account_no_hash)"));
        assertTrue(schema.contains("INDEX ix_account_user_status (user_id, status)"));
        assertTrue(schema.contains(
                "UNIQUE KEY uk_account_transaction_fingerprint (account_id, fingerprint)"));
        assertTrue(schema.contains("INDEX ix_account_transaction_account_date (account_id, tran_date)"));
        assertFalse(schema.contains("platform_id"));
        assertFalse(schema.contains("platform_match_status"));
    }

    @Test
    void schemaUsesForeignKeysOnlyInsideAccountService() {
        assertTrue(schema.contains(
                "FOREIGN KEY (codef_connection_id)\n        REFERENCES CODEF_CONNECTION (codef_connection_id)"));
        assertTrue(schema.contains(
                "FOREIGN KEY (account_id) REFERENCES ACCOUNT (account_id)"));
        assertFalse(schema.contains("FOREIGN KEY (user_id)"));
        assertFalse(schema.contains("FOREIGN KEY (platform_id)"));
    }

    private static void assertEntityColumns(String tableName, Class<?> entityType) {
        Set<String> expectedColumns = Stream.of(entityType.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !Modifier.isTransient(field.getModifiers()))
                .map(field -> field.getName().equals("id")
                        ? tableName.toLowerCase() + "_id"
                        : toSnakeCase(field.getName()))
                .collect(Collectors.toSet());
        assertEquals(expectedColumns, columns(tableName), tableName + " 컬럼이 엔티티 계약과 다릅니다");
    }

    private static void assertMapperCoversColumns(String tableName, String mapperResource) throws IOException {
        String mapper = readResource(mapperResource);
        for (String column : columns(tableName)) {
            assertTrue(Pattern.compile("(?i)\\b" + Pattern.quote(column) + "\\b").matcher(mapper).find(),
                    mapperResource + "에서 " + column + " 컬럼을 찾을 수 없습니다");
        }
    }

    private static Set<String> columns(String tableName) {
        Set<String> actualColumns = new LinkedHashSet<>();
        Matcher columnMatcher = COLUMN_PATTERN.matcher(tableBody(tableName));
        while (columnMatcher.find()) {
            actualColumns.add(columnMatcher.group(1).toLowerCase());
        }
        return actualColumns;
    }

    private static String tableBody(String tableName) {
        Pattern tablePattern = Pattern.compile(
                "(?is)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+`?"
                        + Pattern.quote(tableName)
                        + "`?\\s*\\((.*?)\\)\\s*ENGINE"
        );
        Matcher tableMatcher = tablePattern.matcher(schema);
        assertTrue(tableMatcher.find(), tableName + " 테이블 DDL이 없습니다");
        return tableMatcher.group(1);
    }

    private static String readResource(String resourcePath) throws IOException {
        try (InputStream input = AccountSchemaContractTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertNotNull(input, resourcePath + "가 테스트 classpath에 있어야 합니다");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
