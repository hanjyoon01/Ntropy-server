package com.ntropy.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ntropy.account.api.client.IncomingTransactionQueryClient;
import com.ntropy.account.api.client.VirtualSettlementDepositCommandClient;
import com.ntropy.account.client.LocalIncomingTransactionQueryClient;
import com.ntropy.account.client.LocalVirtualSettlementDepositCommandClient;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.IncomingTransactionQueryMapper;
import com.ntropy.account.mapper.VirtualSettlementDepositMapper;
import com.ntropy.notification.api.client.NotificationCommandClient;
import com.ntropy.work.api.client.VirtualSettlementDepositBatchCommandClient.BatchResult;
import com.ntropy.notification.api.dto.NotificationCreateCommand;
import com.ntropy.notification.api.dto.NotificationSummary;
import com.ntropy.user.api.client.ActiveUserQueryClient;
import com.ntropy.work.adapter.account.AccountIncomingTransactionAdapter;
import com.ntropy.work.adapter.account.AccountSettlementDepositAdapter;
import com.ntropy.work.adapter.notification.NotificationAdapter;
import com.ntropy.work.adapter.user.UserActiveUsersAdapter;
import com.ntropy.work.client.LocalVirtualSettlementDepositBatchCommandClient;
import com.ntropy.work.config.SettlementBatchUserScopeProperties;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.JobPlatformMappingMapper;
import com.ntropy.work.mapper.PlatformMapper;
import com.ntropy.work.mapper.SettlementMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.port.account.IncomingTransactionPort;
import com.ntropy.work.port.account.SettlementDepositPort;
import com.ntropy.work.port.notification.NotificationPort;
import com.ntropy.work.port.user.UserPort;
import com.ntropy.work.service.HolidayService;
import com.ntropy.work.service.SettlementService;
import com.ntropy.work.service.VirtualSettlementDepositService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #216(가상 정산 입금 생성)이 실제 MySQL에서 근무일지 확정 -&gt; 가상계좌 입금 -&gt; 정산
 * 완료까지 전체 흐름대로 동작하는지 검증하는 수동 테스트입니다.
 *
 * <p>과거 날짜로 확정된 근무일지 1건을 직접 시딩한 뒤, 이슈 #222에서 추가한
 * {@link LocalVirtualSettlementDepositBatchCommandClient}(스웨거 테스트 엔드포인트가 호출하는
 * 것과 동일한 클래스)를 그대로 호출해 가상 입금 생성과 정산 매칭을 한 번에 실행합니다.
 * account-service의 ACCOUNT_TRANSACTION/ACCOUNT.balance와 work-service의
 * WORK_LOG_PLATFORM_INCOME/WORK_LOG/SETTLEMENT를 모두 실제 DB에서 재조회해 검증하므로,
 * work-service와 account-service가 common 계약을 통해 실제로 맞물려 동작하는지까지 확인합니다.
 * RUN_VIRTUAL_SETTLEMENT_DEPOSIT_TEST=true일 때만 실행합니다. work-service/account-service를
 * 모두 참조해야 해서 두 모듈을 전부 의존성으로 갖는 api 모듈에 둡니다
 * (DiagnosisFinalizationManualVerificationTest와 동일한 이유).</p>
 */
class VirtualSettlementDepositEndToEndManualVerificationTest {

    private static final Long USER_ID = 9_999_999_222L;
    private static final Long PLATFORM_ID = 999_222L;
    private static final String DEPOSIT_NAME = "E2E테스트정산";

    @Test
    void confirmedPastWorkLog_createsVirtualDepositAndCompletesSettlement() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_VIRTUAL_SETTLEMENT_DEPOSIT_TEST")),
                "실제 MySQL이 필요한 이슈 #216/#222 종단 검증용 테스트"
        );

        LocalDate workDate = LocalDate.now().minusDays(10);
        LocalDate expectedDepositDate = workDate.plusDays(1); // DAILY + CALENDAR_DAY offset=1

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            long logId = seedConfirmedPastWorkLog(dataSource, workDate);

            LocalVirtualSettlementDepositBatchCommandClient batchClient =
                    context.getBean(LocalVirtualSettlementDepositBatchCommandClient.class);

            BatchResult result = batchClient.runForDate(USER_ID, LocalDate.now());

            assertEquals(1, result.createdDepositCount(), "가상 입금이 생성되지 않았습니다");
            assertEquals(1, result.matchedSettlementCount(), "생성된 입금이 정산 매칭되지 않았습니다");

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {

                try (ResultSet rs = statement.executeQuery(
                        "SELECT at.in_amount, at.tran_date, at.transaction_category, a.balance "
                                + "FROM ACCOUNT_TRANSACTION at JOIN ACCOUNT a ON a.account_id = at.account_id "
                                + "WHERE a.user_id = " + USER_ID)) {
                    assertTrue(rs.next(), "ACCOUNT_TRANSACTION에 가상 입금 거래가 없습니다");
                    assertEquals(0, BigDecimal.valueOf(45_000).compareTo(rs.getBigDecimal("in_amount")),
                            "입금액이 예상 소득과 다릅니다");
                    assertEquals(expectedDepositDate.toString(), rs.getDate("tran_date").toLocalDate().toString());
                    assertEquals("ORDINARY", rs.getString("transaction_category"));
                    assertEquals(0, BigDecimal.valueOf(145_000).compareTo(rs.getBigDecimal("balance")),
                            "가상계좌 잔액이 입금액만큼 반영되지 않았습니다");
                }

                try (ResultSet rs = statement.executeQuery(
                        "SELECT settlement_status FROM WORK_LOG_PLATFORM_INCOME WHERE log_id = " + logId)) {
                    assertTrue(rs.next());
                    assertEquals("COMPLETED", rs.getString("settlement_status"),
                            "플랫폼 소득이 정산 완료로 갱신되지 않았습니다");
                }

                try (ResultSet rs = statement.executeQuery(
                        "SELECT settlement_status FROM WORK_LOG WHERE log_id = " + logId)) {
                    assertTrue(rs.next());
                    assertEquals("COMPLETED", rs.getString("settlement_status"),
                            "근무일지 정산 상태가 정산 완료로 갱신되지 않았습니다");
                }

                try (ResultSet rs = statement.executeQuery(
                        "SELECT expected_amount, actual_amount, deposit_date FROM SETTLEMENT "
                                + "WHERE user_id = " + USER_ID)) {
                    assertTrue(rs.next(), "SETTLEMENT 행이 생성되지 않았습니다");
                    assertEquals(45_000L, rs.getLong("expected_amount"));
                    assertEquals(45_000L, rs.getLong("actual_amount"));
                    assertEquals(expectedDepositDate.toString(), rs.getDate("deposit_date").toLocalDate().toString());
                }
            }

            System.out.println("VIRTUAL_SETTLEMENT_DEPOSIT_TEST_WORK_DATE=" + workDate);
            System.out.println("VIRTUAL_SETTLEMENT_DEPOSIT_TEST_DEPOSIT_DATE=" + expectedDepositDate);
            System.out.println("VIRTUAL_SETTLEMENT_DEPOSIT_TEST_RESULT=" + result);
        }
    }

    /** 확정된(CONFIRMED) 근무일지 1건 + 플랫폼 소득 + 매칭용 NTROPY 가상 수시입출금 계좌를 시딩한다. */
    private long seedConfirmedPastWorkLog(DataSource dataSource, LocalDate workDate) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 이전 실행 잔여 데이터 정리(이 테스트 전용 user_id/platform_id 범위만).
            statement.execute("DELETE FROM SETTLEMENT WHERE user_id = " + USER_ID);
            statement.execute(
                    "DELETE FROM WORK_LOG_PLATFORM_INCOME WHERE platform_id = " + PLATFORM_ID);
            statement.execute("DELETE FROM WORK_LOG WHERE user_id = " + USER_ID);
            statement.execute("DELETE FROM JOBPLATFORMMAPPING WHERE platform_id = " + PLATFORM_ID);
            statement.execute("DELETE FROM JOB WHERE user_id = " + USER_ID);
            statement.execute("DELETE FROM PLATFORM WHERE platform_id = " + PLATFORM_ID);
            statement.execute(
                    "DELETE FROM ACCOUNT_TRANSACTION WHERE account_id IN ("
                            + "SELECT account_id FROM ACCOUNT WHERE user_id = " + USER_ID + ")"
            );
            statement.execute("DELETE FROM ACCOUNT WHERE user_id = " + USER_ID);
            statement.execute("DELETE FROM CODEF_CONNECTION WHERE user_id = " + USER_ID);

            // 플랫폼: DAILY + CALENDAR_DAY, offset=1 -> 근무일 다음 날이 예상 정산일.
            statement.execute(
                    "INSERT INTO PLATFORM (platform_id, category_id, platform_name, deposit_name, "
                            + "settlement_cycle, settlement_trigger_type, settlement_offset_day, "
                            + "settlement_offset_unit) VALUES ("
                            + PLATFORM_ID + ", 1, 'E2E테스트플랫폼', '" + DEPOSIT_NAME + "', "
                            + "'DAILY', 'AUTO', 1, 'CALENDAR_DAY')"
            );

            statement.execute(
                    "INSERT INTO JOB (user_id, category_id, job_name, settlement_type, hourly_wage, "
                            + "is_regular, base_fatigue, created_at, updated_at, is_active) VALUES ("
                            + USER_ID + ", 1, 'E2E테스트잡', 'HOURLY', 15000, 1, 1, NOW(), NOW(), 1)"
            );
            long jobId = lastInsertId(statement);

            statement.execute(
                    "INSERT INTO JOBPLATFORMMAPPING (job_id, platform_id) VALUES ("
                            + jobId + ", " + PLATFORM_ID + ")"
            );

            // 근무일지 확정: 실제 화면에서 "확정" 버튼을 눌렀을 때와 동일한 상태(CONFIRMED)로 시딩한다.
            statement.execute(
                    "INSERT INTO WORK_LOG (user_id, job_id, work_date, fatigue, status, settlement_status) "
                            + "VALUES (" + USER_ID + ", " + jobId + ", '" + workDate + "', 1, "
                            + "'CONFIRMED', 'PENDING')"
            );
            long logId = lastInsertId(statement);

            statement.execute(
                    "INSERT INTO WORK_LOG_PLATFORM_INCOME (log_id, platform_id, expected_amount, "
                            + "settlement_status) VALUES ("
                            + logId + ", " + PLATFORM_ID + ", 45000, 'PENDING')"
            );

            // NTROPY 가상 수시입출금 계좌: LocalVirtualSettlementDepositCommandClient가 입금 대상으로
            // 조회하는 조건(provider=NTROPY, account_group=DEPOSIT_TRUST, deposit_type_code=11)과 동일하게 시딩.
            statement.execute(
                    "INSERT INTO CODEF_CONNECTION (user_id, provider, connected_id) VALUES ("
                            + USER_ID + ", 'NTROPY', 'NTROPY-e2e-222')"
            );
            long connectionId = lastInsertId(statement);

            statement.execute(
                    "INSERT INTO ACCOUNT (codef_connection_id, user_id, organization_code, account_group, "
                            + "deposit_type_code, account_no_masked, account_no_hash, balance, currency_code, "
                            + "overdraft_yn, status) VALUES ("
                            + connectionId + ", " + USER_ID + ", '0088', 'DEPOSIT_TRUST', '11', "
                            + "'****9222', SHA2('e2e-222-account', 256), 100000, 'KRW', 0, 'ACTIVE')"
            );

            return logId;
        }
    }

    private long lastInsertId(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static class NoopNotificationCommandClient implements NotificationCommandClient {
        @Override
        public NotificationSummary create(NotificationCreateCommand command) {
            return null;
        }

        @Override
        public void markAsRead(Long userId, Long notificationId) {
        }

        @Override
        public void delete(Long userId, Long notificationId) {
        }
    }

    private static class NoHolidayService extends HolidayService {
        private NoHolidayService() {
            super(null, null);
        }

        @Override
        public Set<LocalDate> getHolidays(LocalDate startDate, LocalDate endDate) {
            return Set.of();
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {PlatformMapper.class, AccountMapper.class})
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(System.getenv().getOrDefault(
                    "VIRTUAL_SETTLEMENT_TEST_DB_URL",
                    "jdbc:mysql://localhost:3307/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            ));
            config.setUsername(System.getenv().getOrDefault("VIRTUAL_SETTLEMENT_TEST_DB_USERNAME", "root"));
            config.setPassword(System.getenv().getOrDefault("VIRTUAL_SETTLEMENT_TEST_DB_PASSWORD", "root"));
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

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ActiveUserQueryClient activeUserQueryClient() {
            return scope -> List.of(USER_ID);
        }

        @Bean
        SettlementBatchUserScopeProperties settlementBatchUserScopeProperties() {
            return new SettlementBatchUserScopeProperties("ALL");
        }

        @Bean
        HolidayService holidayService() {
            return new NoHolidayService();
        }

        @Bean
        NotificationCommandClient notificationCommandClient() {
            return new NoopNotificationCommandClient();
        }

        @Bean
        VirtualSettlementDepositCommandClient virtualSettlementDepositCommandClient(
                AccountMapper accountMapper, VirtualSettlementDepositMapper virtualSettlementDepositMapper) {
            return new LocalVirtualSettlementDepositCommandClient(accountMapper, virtualSettlementDepositMapper);
        }

        @Bean
        IncomingTransactionQueryClient incomingTransactionQueryClient(
                IncomingTransactionQueryMapper incomingTransactionQueryMapper) {
            return new LocalIncomingTransactionQueryClient(incomingTransactionQueryMapper);
        }

        @Bean
        IncomingTransactionPort incomingTransactionPort(
                IncomingTransactionQueryClient incomingTransactionQueryClient) {
            return new AccountIncomingTransactionAdapter(incomingTransactionQueryClient);
        }

        @Bean
        SettlementDepositPort settlementDepositPort(
                VirtualSettlementDepositCommandClient virtualSettlementDepositCommandClient) {
            return new AccountSettlementDepositAdapter(virtualSettlementDepositCommandClient);
        }

        @Bean
        UserPort userPort(ActiveUserQueryClient activeUserQueryClient) {
            return new UserActiveUsersAdapter(activeUserQueryClient);
        }

        @Bean
        NotificationPort notificationPort(NotificationCommandClient notificationCommandClient) {
            return new NotificationAdapter(notificationCommandClient);
        }

        @Bean
        VirtualSettlementDepositService virtualSettlementDepositService(
                UserPort userPort,
                SettlementBatchUserScopeProperties userScopeProperties,
                WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper,
                PlatformMapper platformMapper,
                HolidayService holidayService,
                SettlementDepositPort settlementDepositPort) {
            return new VirtualSettlementDepositService(
                    userPort, userScopeProperties, workLogPlatformIncomeMapper,
                    platformMapper, holidayService, settlementDepositPort);
        }

        @Bean
        SettlementService settlementService(
                IncomingTransactionPort incomingTransactionPort,
                UserPort userPort,
                SettlementBatchUserScopeProperties userScopeProperties,
                PlatformMapper platformMapper,
                JobMapper jobMapper,
                JobPlatformMappingMapper jobPlatformMappingMapper,
                WorkLogMapper workLogMapper,
                WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper,
                SettlementMapper settlementMapper,
                HolidayService holidayService,
                NotificationPort notificationPort) {
            return new SettlementService(
                    incomingTransactionPort, userPort, userScopeProperties,
                    platformMapper, jobMapper, jobPlatformMappingMapper, workLogMapper,
                    workLogPlatformIncomeMapper, settlementMapper, holidayService, notificationPort);
        }

        @Bean
        LocalVirtualSettlementDepositBatchCommandClient localVirtualSettlementDepositBatchCommandClient(
                VirtualSettlementDepositService virtualSettlementDepositService,
                SettlementService settlementService) {
            return new LocalVirtualSettlementDepositBatchCommandClient(
                    virtualSettlementDepositService, settlementService);
        }
    }
}
