package com.ntropy.account.integration.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.domain.entity.DailyBatchExecution;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.mapper.DailyBatchExecutionMapper;
import com.ntropy.account.service.BatchExecutionLeaseService;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #158: lease 획득의 row-count 기반 동시성, owner_id/lease_token fencing, watermark의
 * EXISTS 서브쿼리 fencing이 실제 MySQL에서 의도한 대로 동작하는지 검증한다.
 * {@code BatchExecutionLeaseServiceTest}는 메모리 fake로 서비스 로직만 검증하므로,
 * 실제 SQL 문법·행 잠금 동작은 여기서만 잡을 수 있다.
 * RUN_DAILY_SYNC_LEASE_TEST=true일 때만 실행한다.
 */
class DailyBatchExecutionLeaseManualVerificationTest {

    private static final String JOB_NAME = "daily-sync-codef-test";
    private static final Long TEST_USER_ID = 9_999_999_158L;

    @Test
    void leaseFencingAndWatermarkBehaveCorrectlyAgainstRealMysql() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_DAILY_SYNC_LEASE_TEST")),
                "실제 MySQL이 필요한 이슈 #158 lease/fencing 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = ctx.getBean(DataSource.class);
            ensureSchema(dataSource);

            DailyBatchExecutionMapper executionMapper = ctx.getBean(DailyBatchExecutionMapper.class);
            AccountSyncStateMapper syncStateMapper = ctx.getBean(AccountSyncStateMapper.class);
            CodefConnectionMapper codefConnectionMapper = ctx.getBean(CodefConnectionMapper.class);

            LocalDate businessDate = LocalDate.now().minusDays(1); // 매 실행마다 새 업무일로 격리
            cleanUp(dataSource, businessDate);

            Long connectionId = ensureCodefConnection(codefConnectionMapper, dataSource);

            // 1) 동시 lease 획득: 이미 만료된 실행 하나를 여러 스레드가 동시에 인계하려 하면 정확히 하나만 성공해야 한다.
            DailyBatchExecution seed = execution(businessDate, "owner-seed", "seed-token",
                    LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(5));
            executionMapper.insert(seed, 360);
            expireLease(dataSource, businessDate);

            int threadCount = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger();
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    DailyBatchExecution attempt = execution(businessDate, "owner-" + index, "token-" + index,
                            LocalDateTime.now().plusMinutes(6), LocalDateTime.now());
                    int updated = executionMapper.acquireExpiredLease(attempt, 360);
                    if (updated == 1) {
                        successCount.incrementAndGet();
                    }
                }));
            }
            ready.await();
            go.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertEquals(1, successCount.get(), "만료된 lease는 정확히 한 인스턴스만 인계받아야 합니다");

            DailyBatchExecution afterTakeover = executionMapper.findByJobNameAndBusinessDate(JOB_NAME, businessDate);
            assertNotNull(afterTakeover);
            assertEquals(BatchExecutionStatus.RUNNING, afterTakeover.getStatus());

            // 2) fencing: 인계 전 소유자(owner-seed)가 뒤늦게 heartbeat/완료를 시도해도 거부돼야 한다.
            int staleHeartbeat = executionMapper.renewLease(
                    afterTakeover.getId(), "owner-seed", "seed-token", 600
            );
            assertEquals(0, staleHeartbeat, "인계 전 owner의 heartbeat는 거부돼야 합니다");

            int staleComplete = executionMapper.completeIfOwner(
                    afterTakeover.getId(), "owner-seed", "seed-token",
                    BatchExecutionStatus.SUCCESS.name(), null
            );
            assertEquals(0, staleComplete, "인계 전 owner의 완료 처리는 거부돼야 합니다");

            // 3) 현재 소유자의 heartbeat/watermark 갱신은 성공해야 한다.
            int validHeartbeat = executionMapper.renewLease(
                    afterTakeover.getId(), afterTakeover.getOwnerId(), afterTakeover.getLeaseToken(), 360
            );
            assertEquals(1, validHeartbeat, "현재 owner의 heartbeat는 성공해야 합니다");

            syncStateMapper.insertIfAbsent(pendingSyncState(connectionId));
            int watermarkAdvanced = syncStateMapper.advanceIfOwner(
                    connectionId, "0004", "SUCCESS", null,
                    JOB_NAME, businessDate, afterTakeover.getOwnerId(), afterTakeover.getLeaseToken()
            );
            assertEquals(1, watermarkAdvanced, "현재 owner의 watermark 갱신은 성공해야 합니다");

            // 4) 인계 전 소유자의 lease_token으로는 watermark도 갱신되면 안 된다.
            int staleWatermark = syncStateMapper.advanceIfOwner(
                    connectionId, "0004", "SUCCESS", null,
                    JOB_NAME, businessDate, "owner-seed", "seed-token"
            );
            assertEquals(0, staleWatermark, "인계 전 owner의 watermark 갱신은 거부돼야 합니다");

            // 5) 완료 후에는 같은 업무일 재실행(수동 재시도)이 허용돼야 한다.
            int completed = executionMapper.completeIfOwner(
                    afterTakeover.getId(), afterTakeover.getOwnerId(), afterTakeover.getLeaseToken(),
                    BatchExecutionStatus.SUCCESS.name(), null
            );
            assertEquals(1, completed);

            DailyBatchExecution rerunAttempt = execution(businessDate, "owner-rerun", "token-rerun",
                    LocalDateTime.now().plusMinutes(6), LocalDateTime.now());
            int rerunAcquired = executionMapper.acquireExpiredLease(rerunAttempt, 360);
            assertEquals(1, rerunAcquired, "SUCCESS로 완료된 실행의 재호출은 허용돼야 합니다");

            cleanUp(dataSource, businessDate);
        }
    }

    /**
     * {@link BatchExecutionLeaseService}가 획득·heartbeat·완료 SQL 안에서 직접 {@code NOW()}를
     * 평가하는 경로가 실제 MySQL에서 동작하는지 검증한다.
     */
    @Test
    void batchExecutionLeaseServiceCurrentTimeBasedLifecycleWorksAgainstRealMysql() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_DAILY_SYNC_LEASE_TEST")),
                "실제 MySQL이 필요한 이슈 #158 lease/fencing 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = ctx.getBean(DataSource.class);
            ensureSchema(dataSource);

            LocalDate businessDate = LocalDate.now().minusDays(2); // 다른 테스트와 업무일을 분리
            cleanUp(dataSource, businessDate);

            BatchExecutionLeaseService leaseService = ctx.getBean(BatchExecutionLeaseService.class);
            LeaseHandle lease = leaseService.acquire(JOB_NAME, businessDate, "owner-real-service").orElseThrow(
                    () -> new AssertionError("lease 획득에 실패했습니다")
            );

            assertEquals(true, leaseService.heartbeat(lease), "실제 서비스의 heartbeat가 성공해야 합니다");
            assertEquals(true, leaseService.complete(lease, BatchExecutionStatus.SUCCESS, null),
                    "실제 서비스의 완료 처리가 성공해야 합니다");

            cleanUp(dataSource, businessDate);
        }
    }

    private static DailyBatchExecution execution(LocalDate businessDate, String ownerId, String leaseToken,
                                                  LocalDateTime leaseUntil, LocalDateTime startedAt) {
        DailyBatchExecution execution = new DailyBatchExecution();
        execution.setJobName(JOB_NAME);
        execution.setBusinessDate(businessDate);
        execution.setStatus(BatchExecutionStatus.RUNNING);
        execution.setOwnerId(ownerId);
        execution.setLeaseToken(leaseToken);
        execution.setLeaseUntil(leaseUntil);
        execution.setStartedAt(startedAt);
        return execution;
    }

    private static AccountSyncState pendingSyncState(Long connectionId) {
        AccountSyncState state = new AccountSyncState();
        state.setCodefConnectionId(connectionId);
        state.setOrganizationCode("0004");
        state.setLastStatus(com.ntropy.account.domain.AccountSyncStatus.PENDING);
        return state;
    }

    private static Long ensureCodefConnection(CodefConnectionMapper mapper, DataSource dataSource)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM CODEF_CONNECTION WHERE user_id = " + TEST_USER_ID);
        }
        CodefConnection connection = new CodefConnection();
        connection.setUserId(TEST_USER_ID);
        connection.setProvider("CODEF");
        connection.setConnectedId("lease-test-" + UUID.randomUUID());
        mapper.upsert(connection);
        return mapper.findByUserIdAndProvider(TEST_USER_ID, "CODEF").getId();
    }

    private static void cleanUp(DataSource dataSource, LocalDate businessDate) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "DELETE FROM DAILY_BATCH_EXECUTION WHERE job_name = '" + JOB_NAME
                            + "' AND business_date = '" + businessDate + "'"
            );
            statement.executeUpdate(
                    "DELETE FROM ACCOUNT_SYNC_STATE WHERE codef_connection_id IN "
                            + "(SELECT codef_connection_id FROM CODEF_CONNECTION WHERE user_id = " + TEST_USER_ID + ")"
            );
        }
    }

    private static void expireLease(DataSource dataSource, LocalDate businessDate) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE DAILY_BATCH_EXECUTION SET lease_until = DATE_SUB(NOW(), INTERVAL 1 SECOND)"
                            + " WHERE job_name = '" + JOB_NAME + "' AND business_date = '" + businessDate + "'"
            );
        }
    }

    private static void ensureSchema(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS DAILY_BATCH_EXECUTION
                    (
                        daily_batch_execution_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        job_name      VARCHAR(50)  NOT NULL,
                        business_date DATE         NOT NULL,
                        status        VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
                        owner_id      VARCHAR(100) NOT NULL,
                        lease_token   VARCHAR(36)  NOT NULL,
                        lease_until   DATETIME     NOT NULL,
                        started_at    DATETIME     NOT NULL,
                        completed_at  DATETIME     NULL,
                        error_summary JSON         NULL,
                        created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_daily_batch_execution_job_date (job_name, business_date)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ACCOUNT_SYNC_STATE
                    (
                        account_sync_state_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
                        codef_connection_id       BIGINT      NOT NULL,
                        organization_code         VARCHAR(10) NOT NULL,
                        last_successful_synced_at DATETIME    NULL,
                        last_status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                        last_error_code             VARCHAR(50) NULL,
                        created_at                  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at                  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_account_sync_state_connection_org (codef_connection_id, organization_code),
                        CONSTRAINT fk_account_sync_state_codef_connection FOREIGN KEY (codef_connection_id)
                            REFERENCES CODEF_CONNECTION (codef_connection_id)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
                    """);
        }
    }

    @Configuration
    @ComponentScan(basePackages = "com.ntropy.account")
    @MapperScan("com.ntropy.account.mapper")
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:33306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
            config.setUsername("root");
            config.setPassword("root");
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return new HikariDataSource(config);
        }

        @Bean
        SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setMapperLocations(
                    new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")
            );
            return factoryBean;
        }
    }
}
