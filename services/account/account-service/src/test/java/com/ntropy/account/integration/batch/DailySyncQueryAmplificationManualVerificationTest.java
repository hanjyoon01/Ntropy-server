package com.ntropy.account.integration.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.CodefBankTransactionClient;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.AccountNoMask;
import com.ntropy.account.domain.Batching;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.AccountCollectionService;
import com.ntropy.account.service.PersonalBankAccountService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #233 실제 MySQL 검증용 수동 테스트. 쿼리 수는 사용자×기관 서비스 경계로 재현하고,
 * 시간은 워밍업 후 Before/After 순서를 번갈아 실행해 median/p95를 출력한다.
 * RUN_DAILY_SYNC_QUERY_BENCHMARK_TEST=true일 때만 실행한다.
 */
class DailySyncQueryAmplificationManualVerificationTest {

    private static final long BASE_USER_ID = 970_000_000L;
    private static final int USER_COUNT = 100;
    private static final int ORG_PER_USER = 3;
    private static final int ACCOUNT_PER_ORG = 10;
    private static final int TOTAL_ACCOUNTS = USER_COUNT * ORG_PER_USER * ACCOUNT_PER_ORG;
    private static final int ACCOUNT_UPSERT_BATCH_SIZE = 200;
    private static final int USER_ID_CHUNK_SIZE = 500;
    private static final List<String> ORGS = List.of("0004", "0088", "0011");
    private static final Path REPORT_PATH = Path.of(
            "build", "reports", "benchmarks", "daily-sync-query-amplification.txt"
    );

    @Test
    void verifiesBulkMapperSemanticsAndServicePathAgainstRealMysql() throws Exception {
        requireOptIn();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            CodefConnectionMapper connectionMapper = context.getBean(CodefConnectionMapper.class);
            AccountMapper accountMapper = context.getBean(AccountMapper.class);
            AccountTransactionMapper transactionMapper = context.getBean(AccountTransactionMapper.class);
            SqlExecutionCountInterceptor interceptor = context.getBean(SqlExecutionCountInterceptor.class);
            cleanUp(dataSource);
            try {
                CodefConnection connection = newConnection(BASE_USER_ID, "CODEF");
                connectionMapper.upsert(connection);
                connection = connectionMapper.findByUserIdAndProvider(BASE_USER_ID, "CODEF");
                assertNotNull(connection);
                assertEquals(1, connectionMapper.findByUserIdsAndProvider(
                        List.of(BASE_USER_ID), "CODEF").size());

                Account original = newAccount(connection.getId(), BASE_USER_ID, ORGS.get(0), "SEMANTIC-1", "old");
                original.setInterestRate(new BigDecimal("2.50"));
                original.setMaturityDate(LocalDate.of(2028, 1, 1));
                Account second = newAccount(connection.getId(), BASE_USER_ID, ORGS.get(0), "SEMANTIC-2", "second");
                accountMapper.upsertAll(List.of(original, second));
                Map<String, Account> firstRead = byHash(accountMapper.findByConnectionIdAndAccountNoHashes(
                        connection.getId(), List.of(original.getAccountNoHash(), second.getAccountNoHash())));
                assertEquals(2, firstRead.size());
                Account persisted = firstRead.get(original.getAccountNoHash());
                assertNotNull(persisted.getId());
                assertNotNull(persisted.getCreatedAt());

                Account update = newAccount(connection.getId(), BASE_USER_ID, ORGS.get(0), "SEMANTIC-1", "new");
                update.setBalance(new BigDecimal("99.00"));
                update.setInterestRate(new BigDecimal("9.99"));
                accountMapper.upsertAll(List.of(update));
                Account updated = accountMapper.findByConnectionIdAndAccountNoHash(
                        connection.getId(), original.getAccountNoHash());
                assertEquals(persisted.getId(), updated.getId());
                assertEquals("new", updated.getAccountName());
                assertEquals(0, new BigDecimal("99.00").compareTo(updated.getBalance()));
                assertEquals(0, new BigDecimal("2.50").compareTo(updated.getInterestRate()));
                assertEquals(LocalDate.of(2028, 1, 1), updated.getMaturityDate());

                ObjectMapper objectMapper = new ObjectMapper();
                StubBankTransactionClient transactionClient = new StubBankTransactionClient(
                        objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}"));
                AccountCollectionService service = new AccountCollectionService(
                        new StubPersonalBankAccountService(objectMapper.readTree(smokeAccountListJson())),
                        connectionMapper, transactionClient, null, null, accountMapper, transactionMapper);
                CodefConnection suppliedConnection = connection;
                BenchmarkResult smoke = measure(interceptor, () -> {
                    List<AccountCollectionService.AccountCollectionOutcome> outcomes = service.collectForDailySync(
                            BASE_USER_ID, PersonalBank.SHINHAN_BANK, suppliedConnection, null,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), () -> true);
                    assertEquals(3, outcomes.size());
                    assertTrue(outcomes.stream().allMatch(outcome ->
                            outcome.status() == AccountCollectionService.AccountCollectionOutcome.Status.SUCCESS));
                });
                assertEquals(2, smoke.sqlCount(), "서비스 경로는 bulk upsert 1회 + bulk 조회 1회여야 합니다");
                assertEquals(3, transactionClient.calls);
            } finally {
                cleanUp(dataSource);
            }
        }
    }

    @Test
    void measuresQueryAmplificationBeforeAndAfterAgainstRealMysql() throws Exception {
        requireOptIn();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            SqlExecutionCountInterceptor interceptor = context.getBean(SqlExecutionCountInterceptor.class);
            CodefConnectionMapper connectionMapper = context.getBean(CodefConnectionMapper.class);
            AccountMapper accountMapper = context.getBean(AccountMapper.class);
            cleanUp(dataSource);
            try {
                List<Long> userIds = userIds();
                seedConnections(connectionMapper, userIds);
                Map<Long, CodefConnection> codefConnections = byUser(
                        connectionMapper.findByUserIdsAndProvider(userIds, "CODEF"));
                Map<Long, CodefConnection> ntropyConnections = byUser(
                        connectionMapper.findByUserIdsAndProvider(userIds, "NTROPY"));
                seedNtropyAccounts(accountMapper, userIds, ntropyConnections);
                List<AccountFixture> beforeAccounts = fixtures(codefConnections, "BEFORE");
                List<AccountFixture> afterAccounts = fixtures(codefConnections, "AFTER");

                BenchmarkResult connectionBefore = measure(interceptor,
                        () -> runConnectionsBefore(connectionMapper, userIds));
                BenchmarkResult connectionAfter = measure(interceptor,
                        () -> runConnectionsAfter(connectionMapper, userIds));
                BenchmarkResult duplicateBefore = measure(interceptor,
                        () -> runDuplicateConnectionsBefore(connectionMapper, userIds));
                BenchmarkResult duplicateAfter = measure(interceptor, () -> { });
                BenchmarkResult accountBefore = measure(interceptor,
                        () -> runAccountsBefore(accountMapper, beforeAccounts));
                BenchmarkResult accountAfter = measure(interceptor,
                        () -> runAccountsAfter(accountMapper, afterAccounts));
                BenchmarkResult ntropyBefore = measure(interceptor,
                        () -> runNtropyBefore(accountMapper, userIds));
                BenchmarkResult ntropyAfter = measure(interceptor,
                        () -> runNtropyAfter(accountMapper, userIds));

                assertEquals(200, connectionBefore.sqlCount());
                assertEquals(2, connectionAfter.sqlCount());
                assertEquals(300, duplicateBefore.sqlCount());
                assertEquals(0, duplicateAfter.sqlCount());
                assertEquals(6_000, accountBefore.sqlCount());
                assertEquals(600, accountAfter.sqlCount(),
                        "사용자×기관별 계좌 10개는 각각 upsertAll 1회 + findMany 1회여야 합니다");
                assertEquals(300, ntropyBefore.sqlCount());
                assertEquals(100, ntropyAfter.sqlCount());
                int totalBefore = sum(connectionBefore, duplicateBefore, accountBefore, ntropyBefore);
                int totalAfter = sum(connectionAfter, duplicateAfter, accountAfter, ntropyAfter);
                assertEquals(6_800, totalBefore);
                assertEquals(702, totalAfter);

                Runnable fullBefore = () -> runFullBefore(
                        connectionMapper, accountMapper, userIds, beforeAccounts);
                Runnable fullAfter = () -> runFullAfter(
                        connectionMapper, accountMapper, userIds, afterAccounts);
                int warmups = positiveEnvironmentInteger("DAILY_SYNC_BENCHMARK_WARMUPS", 1);
                int iterations = positiveEnvironmentInteger("DAILY_SYNC_BENCHMARK_ITERATIONS", 5);
                for (int i = 0; i < warmups; i++) {
                    assertEquals(totalBefore, measure(interceptor, fullBefore).sqlCount());
                    assertEquals(totalAfter, measure(interceptor, fullAfter).sqlCount());
                }
                List<Long> beforeNanos = new ArrayList<>();
                List<Long> afterNanos = new ArrayList<>();
                for (int i = 0; i < iterations; i++) {
                    if (i % 2 == 0) {
                        addSample(interceptor, fullBefore, totalBefore, beforeNanos);
                        addSample(interceptor, fullAfter, totalAfter, afterNanos);
                    } else {
                        addSample(interceptor, fullAfter, totalAfter, afterNanos);
                        addSample(interceptor, fullBefore, totalBefore, beforeNanos);
                    }
                }
                TimingSummary beforeTiming = TimingSummary.from(beforeNanos);
                TimingSummary afterTiming = TimingSummary.from(afterNanos);

                String connectionPlan = explainAnalyze(dataSource, connectionLookupSql(userIds.subList(0, 2)));
                String accountPlan = explainAnalyze(dataSource, accountLookupSql(afterAccounts.get(0)));
                assertIndexRangePlan(connectionPlan, "uk_codef_connection_user_provider", "CODEF_CONNECTION");
                assertIndexRangePlan(accountPlan, "uk_account_connection_hash", "ACCOUNT");
                assertFalse(connectionPlan.toLowerCase().contains("covering"));
                assertFalse(accountPlan.toLowerCase().contains("covering"));

                String report = buildReport(dataSource, warmups, iterations,
                        connectionBefore, connectionAfter, duplicateBefore, duplicateAfter,
                        accountBefore, accountAfter, ntropyBefore, ntropyAfter,
                        totalBefore, totalAfter, beforeTiming, afterTiming, connectionPlan, accountPlan);
                Files.createDirectories(REPORT_PATH.getParent());
                Files.writeString(REPORT_PATH, report, StandardCharsets.UTF_8);
                System.out.println(report);
            } finally {
                cleanUp(dataSource);
            }
        }
    }

    private static void requireOptIn() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_DAILY_SYNC_QUERY_BENCHMARK_TEST")),
                "실제 MySQL이 필요한 이슈 #233 검증용 테스트");
    }

    private static void seedConnections(CodefConnectionMapper mapper, List<Long> userIds) {
        for (Long userId : userIds) {
            mapper.upsert(newConnection(userId, "CODEF"));
            mapper.upsert(newConnection(userId, "NTROPY"));
        }
    }

    private static CodefConnection newConnection(Long userId, String provider) {
        CodefConnection connection = new CodefConnection();
        connection.setUserId(userId);
        connection.setProvider(provider);
        connection.setConnectedId(provider.toLowerCase() + "-bench-" + userId);
        if ("CODEF".equals(provider)) {
            connection.setRegisteredInstitutionKeys(InstitutionKeys.serialize(ORGS));
        }
        return connection;
    }

    private static Map<Long, CodefConnection> byUser(List<CodefConnection> connections) {
        Map<Long, CodefConnection> result = new HashMap<>();
        connections.forEach(connection -> result.put(connection.getUserId(), connection));
        assertEquals(USER_COUNT, result.size());
        return result;
    }

    private static void seedNtropyAccounts(AccountMapper mapper, List<Long> userIds,
                                           Map<Long, CodefConnection> connections) {
        for (Long userId : userIds) {
            List<Account> accounts = ORGS.stream().map(org -> newAccount(
                    connections.get(userId).getId(), userId, org,
                    "NTROPY-" + userId + "-" + org, "ntropy")).toList();
            mapper.upsertAll(accounts);
        }
    }

    private static List<AccountFixture> fixtures(Map<Long, CodefConnection> connections, String prefix) {
        List<AccountFixture> result = new ArrayList<>(USER_COUNT * ORG_PER_USER);
        for (Long userId : userIds()) {
            for (String org : ORGS) {
                List<Account> accounts = new ArrayList<>(ACCOUNT_PER_ORG);
                for (int index = 0; index < ACCOUNT_PER_ORG; index++) {
                    accounts.add(newAccount(connections.get(userId).getId(), userId, org,
                            prefix + "-" + userId + "-" + org + "-" + index, prefix.toLowerCase()));
                }
                result.add(new AccountFixture(connections.get(userId).getId(), List.copyOf(accounts)));
            }
        }
        return List.copyOf(result);
    }

    private static Account newAccount(Long connectionId, Long userId, String org,
                                      String rawAccountNo, String accountName) {
        Account account = new Account();
        account.setCodefConnectionId(connectionId);
        account.setUserId(userId);
        account.setOrganizationCode(org);
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("11");
        account.setAccountNoMasked(AccountNoMask.mask(rawAccountNo));
        account.setAccountNoHash(AccountNoHash.hash(org, rawAccountNo));
        account.setAccountName(accountName);
        account.setBalance(BigDecimal.TEN);
        account.setCurrencyCode("KRW");
        return account;
    }

    private static void runConnectionsBefore(CodefConnectionMapper mapper, List<Long> userIds) {
        for (Long userId : userIds) {
            mapper.findByUserIdAndProvider(userId, "CODEF");
            mapper.findByUserIdAndProvider(userId, "NTROPY");
        }
    }

    private static void runConnectionsAfter(CodefConnectionMapper mapper, List<Long> userIds) {
        for (List<Long> chunk : Batching.chunk(userIds, USER_ID_CHUNK_SIZE)) {
            mapper.findByUserIdsAndProvider(chunk, "CODEF");
            mapper.findByUserIdsAndProvider(chunk, "NTROPY");
        }
    }

    private static void runDuplicateConnectionsBefore(CodefConnectionMapper mapper, List<Long> userIds) {
        for (Long userId : userIds) {
            for (int org = 0; org < ORG_PER_USER; org++) {
                mapper.findByUserIdAndProvider(userId, "CODEF");
            }
        }
    }

    private static void runAccountsBefore(AccountMapper mapper, List<AccountFixture> fixtures) {
        for (AccountFixture fixture : fixtures) {
            for (Account account : fixture.accounts()) {
                mapper.upsert(account);
                mapper.findByConnectionIdAndAccountNoHash(fixture.connectionId(), account.getAccountNoHash());
            }
        }
    }

    private static void runAccountsAfter(AccountMapper mapper, List<AccountFixture> fixtures) {
        for (AccountFixture fixture : fixtures) {
            for (List<Account> chunk : Batching.chunk(fixture.accounts(), ACCOUNT_UPSERT_BATCH_SIZE)) {
                mapper.upsertAll(chunk);
                mapper.findByConnectionIdAndAccountNoHashes(fixture.connectionId(),
                        chunk.stream().map(Account::getAccountNoHash).toList());
            }
        }
    }

    private static void runNtropyBefore(AccountMapper mapper, List<Long> userIds) {
        for (Long userId : userIds) {
            for (int org = 0; org < ORG_PER_USER; org++) {
                mapper.findByUserIdAndProvider(userId, "NTROPY");
            }
        }
    }

    private static void runNtropyAfter(AccountMapper mapper, List<Long> userIds) {
        userIds.forEach(userId -> mapper.findByUserIdAndProvider(userId, "NTROPY"));
    }

    private static void runFullBefore(CodefConnectionMapper connectionMapper, AccountMapper accountMapper,
                                      List<Long> userIds, List<AccountFixture> fixtures) {
        runConnectionsBefore(connectionMapper, userIds);
        runDuplicateConnectionsBefore(connectionMapper, userIds);
        runAccountsBefore(accountMapper, fixtures);
        runNtropyBefore(accountMapper, userIds);
    }

    private static void runFullAfter(CodefConnectionMapper connectionMapper, AccountMapper accountMapper,
                                     List<Long> userIds, List<AccountFixture> fixtures) {
        runConnectionsAfter(connectionMapper, userIds);
        runAccountsAfter(accountMapper, fixtures);
        runNtropyAfter(accountMapper, userIds);
    }

    private static int sum(BenchmarkResult... results) {
        int total = 0;
        for (BenchmarkResult result : results) {
            total += result.sqlCount();
        }
        return total;
    }

    private static void addSample(SqlExecutionCountInterceptor interceptor, Runnable scenario,
                                  int expectedCount, List<Long> samples) {
        BenchmarkResult result = measure(interceptor, scenario);
        assertEquals(expectedCount, result.sqlCount());
        samples.add(result.elapsedNanos());
    }

    private static String connectionLookupSql(List<Long> userIds) {
        String ids = userIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        return """
                SELECT codef_connection_id AS id, user_id AS userId, provider,
                       connected_id AS connectedId, registered_institution_keys AS registeredInstitutionKeys,
                       birth_date_ciphertext AS birthDateCiphertext, birth_date_iv AS birthDateIv,
                       birth_date_key_version AS birthDateKeyVersion, created_at AS createdAt, updated_at AS updatedAt
                FROM CODEF_CONNECTION WHERE provider = 'CODEF' AND user_id IN (%s)
                """.formatted(ids);
    }

    private static String accountLookupSql(AccountFixture fixture) {
        String hashes = fixture.accounts().stream().map(Account::getAccountNoHash)
                .map(hash -> "'" + hash + "'").collect(java.util.stream.Collectors.joining(","));
        return """
                SELECT account_id AS id, codef_connection_id AS codefConnectionId, user_id AS userId,
                       organization_code AS organizationCode, account_group AS accountGroup,
                       deposit_type_code AS depositTypeCode, account_no_masked AS accountNoMasked,
                       account_no_hash AS accountNoHash, account_name AS accountName, balance,
                       loan_contract_principal AS loanContractPrincipal, interest_rate AS interestRate,
                       currency_code AS currencyCode, account_start_date AS accountStartDate,
                       maturity_date AS maturityDate, last_tran_date AS lastTranDate,
                       overdraft_yn AS overdraftYn, next_payment_date AS nextPaymentDate,
                       status, deactivated_at AS deactivatedAt, created_at AS createdAt, updated_at AS updatedAt
                FROM ACCOUNT WHERE codef_connection_id = %d AND account_no_hash IN (%s)
                """.formatted(fixture.connectionId(), hashes);
    }

    private static String explainAnalyze(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("EXPLAIN ANALYZE " + sql)) {
            StringBuilder plan = new StringBuilder();
            while (resultSet.next()) {
                plan.append(resultSet.getString(1)).append('\n');
            }
            return plan.toString();
        }
    }

    private static void assertIndexRangePlan(String plan, String index, String table) {
        String normalized = plan.toLowerCase();
        assertTrue(normalized.contains(index.toLowerCase()), table + "가 기대 인덱스를 사용해야 합니다:\n" + plan);
        assertFalse(normalized.contains("table scan on " + table.toLowerCase()),
                table + "에 full table scan이 없어야 합니다:\n" + plan);
    }

    private static String buildReport(DataSource dataSource, int warmups, int iterations,
                                      BenchmarkResult connectionBefore, BenchmarkResult connectionAfter,
                                      BenchmarkResult duplicateBefore, BenchmarkResult duplicateAfter,
                                      BenchmarkResult accountBefore, BenchmarkResult accountAfter,
                                      BenchmarkResult ntropyBefore, BenchmarkResult ntropyAfter,
                                      int totalBefore, int totalAfter,
                                      TimingSummary beforeTiming, TimingSummary afterTiming,
                                      String connectionPlan, String accountPlan) throws SQLException {
        StringBuilder report = new StringBuilder();
        report.append("=== 이슈 #233 실제 MySQL Before/After ===\n")
                .append("데이터셋: 활성 사용자 ").append(USER_COUNT).append("명 × 기관 ").append(ORG_PER_USER)
                .append("개 × 계좌 ").append(ACCOUNT_PER_ORG).append("개 = CODEF 계좌 ")
                .append(TOTAL_ACCOUNTS).append("개\nDB: ").append(databaseVersion(dataSource)).append('\n')
                .append("범위: 개선 대상 MyBatis mapper SQL (CODEF HTTP·lease/state SQL 제외)\n\n")
                .append(String.format("%-34s %10s %10s %10s%n", "측정 항목", "Before", "After", "개선율"));
        appendRow(report, "CODEF+NTROPY 연결 조회 SQL", connectionBefore.sqlCount(), connectionAfter.sqlCount());
        appendRow(report, "기관별 중복 CODEF 연결 조회 SQL", duplicateBefore.sqlCount(), duplicateAfter.sqlCount());
        appendRow(report, "CODEF 계좌 저장/재조회 SQL", accountBefore.sqlCount(), accountAfter.sqlCount());
        appendRow(report, "NTROPY 계좌 조회 SQL", ntropyBefore.sqlCount(), ntropyAfter.sqlCount());
        appendRow(report, "개선 대상 Mapper SQL 합계", totalBefore, totalAfter);
        report.append("\n로컬 MySQL mapper 경로 시간 (워밍업 ").append(warmups)
                .append("회, 교차 실행 샘플 ").append(iterations).append("회; CODEF HTTP 제외)\n")
                .append(String.format("%-16s %12s %12s %10s%n", "통계", "Before", "After", "개선율"));
        appendTimingRow(report, "median", beforeTiming.medianMillis(), afterTiming.medianMillis());
        appendTimingRow(report, "p95", beforeTiming.p95Millis(), afterTiming.p95Millis());
        report.append("Before samples(ms): ").append(beforeTiming.sampleMillis()).append('\n')
                .append("After samples(ms):  ").append(afterTiming.sampleMillis()).append('\n')
                .append("\n[EXPLAIN ANALYZE] findByUserIdsAndProvider 전체 projection\n").append(connectionPlan)
                .append("\n[EXPLAIN ANALYZE] findByConnectionIdAndAccountNoHashes 전체 projection\n")
                .append(accountPlan)
                .append("\n판정: 두 SELECT 모두 unique key 기반 index range lookup이며 full table scan이 없습니다.\n")
                .append("전체 컬럼을 읽으므로 covering index 조회라고 표현하지 않습니다.\n");
        return report.toString();
    }

    private static void appendRow(StringBuilder report, String label, int before, int after) {
        report.append(String.format("%-34s %,10d %,10d %9.1f%%%n",
                label, before, after, improvement(before, after)));
    }

    private static void appendTimingRow(StringBuilder report, String label, double before, double after) {
        report.append(String.format("%-16s %,10.1fms %,10.1fms %9.1f%%%n",
                label, before, after, improvement(before, after)));
    }

    private static double improvement(double before, double after) {
        return before == 0 ? 0 : (1 - after / before) * 100;
    }

    private static String databaseVersion(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static Map<String, Account> byHash(List<Account> accounts) {
        Map<String, Account> result = new HashMap<>();
        accounts.forEach(account -> result.put(account.getAccountNoHash(), account));
        return result;
    }

    private static List<Long> userIds() {
        List<Long> result = new ArrayList<>(USER_COUNT);
        for (int i = 0; i < USER_COUNT; i++) {
            result.add(BASE_USER_ID + i);
        }
        return List.copyOf(result);
    }

    private static int positiveEnvironmentInteger(String name, int defaultValue) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        int value = Integer.parseInt(raw);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static BenchmarkResult measure(SqlExecutionCountInterceptor interceptor, Runnable action) {
        interceptor.reset();
        long start = System.nanoTime();
        action.run();
        return new BenchmarkResult(interceptor.count(), System.nanoTime() - start);
    }

    private static String smokeAccountListJson() {
        return """
                {"result":{"code":"CF-00000"},"data":{"resDepositTrust":[
                  {"resAccount":"SMOKE-001","resAccountDeposit":"11","resAccountBalance":"10000","resAccountCurrency":"KRW"},
                  {"resAccount":"SMOKE-002","resAccountDeposit":"11","resAccountBalance":"20000","resAccountCurrency":"KRW"},
                  {"resAccount":"SMOKE-003","resAccountDeposit":"11","resAccountBalance":"30000","resAccountCurrency":"KRW"}
                ]}}
                """;
    }

    private static void cleanUp(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM ACCOUNT_TRANSACTION WHERE account_id IN (SELECT account_id FROM "
                    + "ACCOUNT WHERE user_id >= " + BASE_USER_ID + " AND user_id < " + (BASE_USER_ID + 10_000) + ")");
            statement.executeUpdate("DELETE FROM ACCOUNT WHERE user_id >= " + BASE_USER_ID
                    + " AND user_id < " + (BASE_USER_ID + 10_000));
            statement.executeUpdate("DELETE FROM ACCOUNT_SYNC_STATE WHERE codef_connection_id IN (SELECT "
                    + "codef_connection_id FROM CODEF_CONNECTION WHERE user_id >= " + BASE_USER_ID
                    + " AND user_id < " + (BASE_USER_ID + 10_000) + ")");
            statement.executeUpdate("DELETE FROM CODEF_CONNECTION WHERE user_id >= " + BASE_USER_ID
                    + " AND user_id < " + (BASE_USER_ID + 10_000));
        }
    }

    private record AccountFixture(Long connectionId, List<Account> accounts) { }
    private record BenchmarkResult(int sqlCount, long elapsedNanos) { }

    private record TimingSummary(double medianMillis, double p95Millis, List<Double> sampleMillis) {
        static TimingSummary from(List<Long> samples) {
            List<Long> sorted = samples.stream().sorted(Comparator.naturalOrder()).toList();
            int size = sorted.size();
            double median = size % 2 == 0
                    ? (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0
                    : sorted.get(size / 2);
            int p95Index = Math.max(0, (int) Math.ceil(size * 0.95) - 1);
            return new TimingSummary(median / 1_000_000.0, sorted.get(p95Index) / 1_000_000.0,
                    samples.stream().map(value -> value / 1_000_000.0).toList());
        }
    }

    private static class StubPersonalBankAccountService extends PersonalBankAccountService {
        private final JsonNode response;
        StubPersonalBankAccountService(JsonNode response) { super(null, null, null); this.response = response; }
        @Override public JsonNode getPersonalAccountList(Long userId, PersonalBank bank) { return response; }
    }

    private static class StubBankTransactionClient extends CodefBankTransactionClient {
        private final JsonNode response;
        private int calls;
        StubBankTransactionClient(JsonNode response) { super(null); this.response = response; }
        @Override
        public JsonNode getPersonalTransactionList(String organizationCode, String connectedId, String account,
                                                   LocalDate startDate, LocalDate endDate, String birthDate,
                                                   boolean includeNullAccountPassword) {
            calls++;
            return response;
        }
    }

    @Intercepts({
            @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
            @Signature(type = Executor.class, method = "query",
                    args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
    })
    static class SqlExecutionCountInterceptor implements Interceptor {
        private final AtomicInteger count = new AtomicInteger();
        @Override public Object intercept(Invocation invocation) throws Throwable {
            count.incrementAndGet();
            return invocation.proceed();
        }
        void reset() { count.set(0); }
        int count() { return count.get(); }
    }

    @Configuration
    @MapperScan("com.ntropy.account.mapper")
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(environment("DAILY_SYNC_BENCHMARK_JDBC_URL",
                    "jdbc:mysql://localhost:3307/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"));
            config.setUsername(environment("DAILY_SYNC_BENCHMARK_DB_USERNAME", "root"));
            config.setPassword(environment("DAILY_SYNC_BENCHMARK_DB_PASSWORD", "root"));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(5);
            return new HikariDataSource(config);
        }
        @Bean SqlExecutionCountInterceptor sqlExecutionCountInterceptor() {
            return new SqlExecutionCountInterceptor();
        }
        @Bean
        SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource, SqlExecutionCountInterceptor interceptor)
                throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/**/*.xml"));
            factory.setPlugins(interceptor);
            return factory;
        }
        private static String environment(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }
}
