package com.ntropy.account.integration.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Clock;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.VirtualAccountRegenerationService;
import com.ntropy.account.service.VirtualConnectionService;
import com.ntropy.account.service.VirtualFinancialDataService;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator;
import com.ntropy.account.port.user.SeededVirtualUserBatch;
import com.ntropy.account.port.user.UserPort;
import com.ntropy.common.domain.UserScope;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * RUN_VIRTUAL_REGENERATION_TEST=true일 때 로컬 MySQL에서 사용자별 NTROPY 재생성을 검증한다.
 * 같은 사용자를 다른 은행으로 재등록해도 계좌·거래가 중복되지 않고, NTROPY CODEF_CONNECTION 행은
 * 재사용되며, 다른 사용자의 데이터는 영향받지 않는지 확인한다.
 */
class VirtualAccountRegenerationManualVerificationTest {

    private static final long TARGET_USER_ID = 9_000_000_085L;
    private static final long OTHER_USER_ID = 9_000_000_086L;

    @Test
    void regeneratesOnlyTargetUsersNtropyDataWithoutDuplicates() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_VIRTUAL_REGENERATION_TEST")),
                "로컬 MySQL이 필요한 NTROPY 재생성 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            VirtualFinancialDataService dataService = context.getBean(VirtualFinancialDataService.class);
            VirtualAccountRegenerationService regenerationService =
                    context.getBean(VirtualAccountRegenerationService.class);
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            dataService.generateForUser(OTHER_USER_ID, PersonalBank.NH_BANK);
            int otherAccountsBefore = countAccounts(jdbc, OTHER_USER_ID);
            int otherTransactionsBefore = countTransactions(jdbc, OTHER_USER_ID);

            GenerationSummary first = dataService.generateForUser(TARGET_USER_ID, PersonalBank.SHINHAN_BANK);
            Long firstConnectionId = connectionId(jdbc, TARGET_USER_ID);
            int firstAccounts = countAccounts(jdbc, TARGET_USER_ID);
            int firstTransactions = countTransactions(jdbc, TARGET_USER_ID);

            GenerationSummary second = regenerationService.regenerateForUser(TARGET_USER_ID, PersonalBank.NH_BANK);
            Long secondConnectionId = connectionId(jdbc, TARGET_USER_ID);
            int secondAccounts = countAccounts(jdbc, TARGET_USER_ID);
            int secondTransactions = countTransactions(jdbc, TARGET_USER_ID);

            assertEquals(firstConnectionId, secondConnectionId, "NTROPY CODEF_CONNECTION 행은 재사용돼야 한다");
            assertEquals(firstAccounts, secondAccounts, "재등록 후에도 계좌 수가 중복 없이 동일해야 한다");
            assertEquals(firstTransactions, secondTransactions, "재등록 후에도 거래 수가 중복 없이 동일해야 한다");
            assertEquals(3, secondAccounts);
            assertTrue(second.transactions() > 0);

            assertEquals(0, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ACCOUNT
                    WHERE user_id = ?
                      AND organization_code = ?
                    """, Integer.class, TARGET_USER_ID, PersonalBank.SHINHAN_BANK.getOrganizationCode()),
                    "재등록 전 은행(신한)의 계좌는 남아있으면 안 된다");

            assertEquals(otherAccountsBefore, countAccounts(jdbc, OTHER_USER_ID), "다른 사용자 계좌는 영향받지 않아야 한다");
            assertEquals(otherTransactionsBefore, countTransactions(jdbc, OTHER_USER_ID),
                    "다른 사용자 거래는 영향받지 않아야 한다");

            System.out.println("REGENERATION_FIRST_ACCOUNTS=" + first.accounts());
            System.out.println("REGENERATION_SECOND_ACCOUNTS=" + second.accounts());
        }
    }

    private static Long connectionId(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForObject("""
                SELECT codef_connection_id FROM CODEF_CONNECTION WHERE user_id = ? AND provider = ?
                """, Long.class, userId, ConnectionProvider.NTROPY.name());
    }

    private static int countAccounts(JdbcTemplate jdbc, long userId) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ACCOUNT account_row
                JOIN CODEF_CONNECTION connection_row
                    ON connection_row.codef_connection_id = account_row.codef_connection_id
                WHERE account_row.user_id = ? AND connection_row.provider = ?
                """, Integer.class, userId, ConnectionProvider.NTROPY.name());
        return value == null ? 0 : value;
    }

    private static int countTransactions(JdbcTemplate jdbc, long userId) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ACCOUNT_TRANSACTION transaction_row
                JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                JOIN CODEF_CONNECTION connection_row
                    ON connection_row.codef_connection_id = account_row.codef_connection_id
                WHERE account_row.user_id = ? AND connection_row.provider = ?
                """, Integer.class, userId, ConnectionProvider.NTROPY.name());
        return value == null ? 0 : value;
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {
            AccountMapper.class,
            AccountTransactionMapper.class,
            CodefConnectionMapper.class
    })
    static class TestConfig {

        @Bean(destroyMethod = "close")
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(
                    "jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            );
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

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        VirtualConnectionService virtualConnectionService(CodefConnectionMapper mapper) {
            return new VirtualConnectionService(mapper);
        }

        @Bean
        VirtualFinancialTransactionGenerator virtualFinancialTransactionGenerator() {
            return new VirtualFinancialTransactionGenerator();
        }

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        UserPort userPort() {
            // 이 테스트는 generateForUser(userId, bank)만 사용하므로 실제로 호출되지 않는다.
            return new UserPort() {
                @Override
                public java.util.List<Long> findActiveUserIds(UserScope scope) {
                    throw new UnsupportedOperationException(
                            "이 수동 검증 테스트는 배치 시딩 경로를 사용하지 않습니다"
                    );
                }

                @Override
                public SeededVirtualUserBatch findSeededVirtualUsers() {
                    throw new UnsupportedOperationException(
                            "이 수동 검증 테스트는 배치 시딩 경로를 사용하지 않습니다"
                    );
                }
            };
        }

        @Bean
        VirtualFinancialDataService virtualFinancialDataService(
                VirtualConnectionService connectionService,
                AccountMapper accountMapper,
                AccountTransactionMapper transactionMapper,
                VirtualFinancialTransactionGenerator generator,
                Clock clock,
                UserPort userPort
        ) {
            return new VirtualFinancialDataService(
                    connectionService, accountMapper, transactionMapper, generator, clock, userPort
            );
        }

        @Bean
        VirtualAccountRegenerationService virtualAccountRegenerationService(
                AccountMapper accountMapper,
                AccountTransactionMapper transactionMapper,
                VirtualFinancialDataService dataService
        ) {
            return new VirtualAccountRegenerationService(accountMapper, transactionMapper, dataService);
        }
    }
}
