package com.ntropy.account.integration.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.port.user.SeededVirtualUser;
import com.ntropy.account.port.user.SeededVirtualUserBatch;
import com.ntropy.account.port.user.UserPort;
import com.ntropy.account.port.user.VirtualDatasetExecutionContext;
import com.ntropy.account.service.VirtualConnectionService;
import com.ntropy.account.service.VirtualFinancialDataService;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator;
import com.ntropy.common.domain.UserScope;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * RUN_VIRTUAL_FINANCIAL_DATA_TEST=true일 때 로컬 MySQL에 FIN-005 목데이터를 적재한다.
 * user-service가 먼저 기동돼 USERS 테이블에 provider=NTROPY_TEST 가상회원을 시딩해 둔 상태여야 한다
 * (VirtualUserSeedInitializer, 이슈 #167). account-service 모듈은 user-service에 컴파일 의존성이
 * 없으므로, 이 테스트는 USERS 테이블을 직접 조회하는 {@link UserPort} 구현을 둔다.
 */
class VirtualFinancialDataManualVerificationTest {

    private static final String VIRTUAL_TEST_PROVIDER = "NTROPY_TEST";
    private static final VirtualDatasetExecutionContext MANUAL_DATASET_CONTEXT = new VirtualDatasetExecutionContext(
            "VIRTUAL-MULTI-DOMAIN-v1", LocalDate.of(2026, 8, 17), 20_260_817L
    );

    @Test
    void generatesIdempotentVirtualFinancialDataset() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_VIRTUAL_FINANCIAL_DATA_TEST")),
                "로컬 MySQL에 FIN-005 목데이터를 적재하는 수동 검증 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            VirtualFinancialDataService service = context.getBean(VirtualFinancialDataService.class);
            UserPort userPort = context.getBean(UserPort.class);
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            SeededVirtualUserBatch dataset = userPort.findSeededVirtualUsers();
            assumeTrue(!dataset.users().isEmpty(),
                    "user-service가 먼저 가상회원을 시딩해 둬야 합니다 (provider=NTROPY_TEST)");

            int userCount = dataset.users().size();
            Set<Long> expectedUserIds = dataset.users().stream()
                    .map(SeededVirtualUser::userId)
                    .collect(Collectors.toSet());

            GenerationSummary first = service.generate();
            GenerationSummary second = service.generate();

            assertEquals(first, second);
            assertEquals(expectedUserIds.size(), userCount,
                    "가상회원 userId는 중복이 없어야 합니다");
            Set<Long> storedAccountUserIds = Set.copyOf(jdbc.queryForList("""
                    SELECT DISTINCT account_row.user_id
                    FROM ACCOUNT account_row
                    JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                    WHERE seeded_user.provider = ?
                    """, Long.class, VIRTUAL_TEST_PROVIDER));
            assertEquals(expectedUserIds, storedAccountUserIds,
                    "생성된 모든 Account.userId는 조회한 가상회원 userId 집합과 정확히 일치해야 합니다");
            assertEquals(userCount, count(jdbc, """
                    SELECT COUNT(*)
                    FROM CODEF_CONNECTION connection_row
                    JOIN USERS seeded_user ON seeded_user.user_id = connection_row.user_id
                    WHERE connection_row.provider = 'NTROPY'
                      AND seeded_user.provider = ?
                    """, VIRTUAL_TEST_PROVIDER));
            assertEquals(userCount * 3, count(jdbc, """
                    SELECT COUNT(*)
                    FROM ACCOUNT account_row
                    JOIN CODEF_CONNECTION connection_row
                      ON connection_row.codef_connection_id = account_row.codef_connection_id
                    JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                    WHERE connection_row.provider = 'NTROPY'
                      AND seeded_user.provider = ?
                    """, VIRTUAL_TEST_PROVIDER));
            assertEquals(0, count(jdbc, """
                    SELECT COUNT(*) FROM (
                        SELECT account_row.user_id
                        FROM ACCOUNT account_row
                        JOIN CODEF_CONNECTION connection_row
                          ON connection_row.codef_connection_id = account_row.codef_connection_id
                        JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                        WHERE connection_row.provider = 'NTROPY'
                          AND seeded_user.provider = ?
                        GROUP BY account_row.user_id
                        HAVING COUNT(*) <> 3
                           OR SUM(CASE WHEN account_row.account_group = 'DEPOSIT_TRUST'
                                            AND account_row.deposit_type_code = '11' THEN 1 ELSE 0 END) <> 1
                           OR SUM(CASE WHEN account_row.account_group = 'DEPOSIT_TRUST'
                                            AND account_row.deposit_type_code = '12' THEN 1 ELSE 0 END) <> 1
                           OR SUM(CASE WHEN account_row.account_group = 'LOAN'
                                            AND account_row.deposit_type_code = '40'
                                            THEN 1 ELSE 0 END) <> 1
                    ) invalid_account_layout
                    """, VIRTUAL_TEST_PROVIDER),
                    "모든 가상회원은 수시입출금·적금·대출 계좌를 각각 하나씩 가져야 한다");
            assertEquals(first.transactions(), count(jdbc, """
                    SELECT COUNT(*)
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                    WHERE seeded_user.provider = ?
                    """, VIRTUAL_TEST_PROVIDER));
            assertEquals(0, count(jdbc, """
                    SELECT COUNT(*) FROM (
                        SELECT transaction_row.account_id, transaction_row.fingerprint
                        FROM ACCOUNT_TRANSACTION transaction_row
                        JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                        JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                        WHERE seeded_user.provider = ?
                        GROUP BY transaction_row.account_id, transaction_row.fingerprint
                        HAVING COUNT(*) > 1
                    ) duplicate_fingerprint
                    """, VIRTUAL_TEST_PROVIDER));
            assertEquals(0, count(jdbc, """
                    SELECT COUNT(*)
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                    WHERE seeded_user.provider = ?
                      AND transaction_row.desc2 IN ('입금', '출금')
                    """, VIRTUAL_TEST_PROVIDER));
            int distinctIncomeCounterparties = count(jdbc, """
                    SELECT COUNT(DISTINCT REPLACE(transaction_row.desc3, '홈)', ''))
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                    WHERE seeded_user.provider = ?
                      AND REPLACE(transaction_row.desc3, '홈)', '') IN (
                          '우아한청년들', '쿠팡이츠정산', '위대한상상', '카카오모빌리티', '펫피플'
                      )
                    """, VIRTUAL_TEST_PROVIDER);
            // 소득 정산처 5종 전체가 등장하려면 사용자 수가 충분해야 한다(기본 50명 기준).
            assertEquals(5, distinctIncomeCounterparties);
            int distinctInsuranceProducts = count(jdbc, """
                    SELECT COUNT(DISTINCT REPLACE(transaction_row.desc3, '홈)', ''))
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    JOIN USERS seeded_user ON seeded_user.user_id = account_row.user_id
                    WHERE seeded_user.provider = ?
                      AND REPLACE(transaction_row.desc3, '홈)', '') IN (
                          '삼성생명 실손보험', 'KB 이륜차 운전자보험'
                      )
                    """, VIRTUAL_TEST_PROVIDER);
            assertEquals(2, distinctInsuranceProducts);

            System.out.println("VIRTUAL_FINANCIAL_USERS=" + first.users());
            System.out.println("VIRTUAL_FINANCIAL_ACCOUNTS=" + first.accounts());
            System.out.println("VIRTUAL_FINANCIAL_INCOME_COUNTERPARTIES=" + first.incomeCounterparties());
            System.out.println("VIRTUAL_FINANCIAL_TRANSACTIONS=" + first.transactions());
        }
    }

    private static int count(JdbcTemplate jdbc, String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
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
            // generate()/generateForUsers()는 이 Clock 대신 VirtualDatasetExecutionContext.referenceDate()를 쓴다.
            return Clock.systemDefaultZone();
        }

        /**
         * user-service(이슈 #167)가 USERS에 시딩한 provider=NTROPY_TEST 가상회원을 직접 조회한다.
         * account-service 모듈은 user-service에 컴파일 의존성이 없어 VirtualUserProviderId를 재사용할
         * 수 없으므로, 같은 provider_id 형식(virtual-user-{순번6자리})만 이 테스트 안에서 다시 파싱한다.
         * 실행 컨텍스트(dataset-version/reference-date/random-seed)는 user-service의 기본값과 같은
         * 고정값을 사용한다. 수동 테스트 실행일이 바뀌어도 생성 결과는 달라지지 않는다.
         * findActiveUserIds는 이 테스트에서 쓰이지 않는다.
         */
        @Bean
        UserPort userPort(DataSource dataSource) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            return new UserPort() {
                @Override
                public List<Long> findActiveUserIds(UserScope scope) {
                    throw new UnsupportedOperationException("이 수동 검증 테스트에서는 사용하지 않습니다");
                }

                @Override
                public SeededVirtualUserBatch findSeededVirtualUsers() {
                    List<SeededVirtualUser> users = jdbc.query(
                            "SELECT user_id, provider_id FROM USERS WHERE provider = ? ORDER BY user_id",
                            (resultSet, rowNumber) -> new SeededVirtualUser(
                                    resultSet.getLong("user_id"),
                                    parseOrdinal(resultSet.getString("provider_id"))
                            ),
                            VIRTUAL_TEST_PROVIDER
                    );
                    List<SeededVirtualUser> sorted = users.stream()
                            .sorted(Comparator.comparingInt(SeededVirtualUser::ordinal))
                            .toList();
                    return new SeededVirtualUserBatch(MANUAL_DATASET_CONTEXT, sorted);
                }
            };
        }

        private static int parseOrdinal(String providerId) {
            String prefix = "virtual-user-";
            if (providerId == null || !providerId.startsWith(prefix)) {
                throw new IllegalStateException("가상회원 provider_id 형식이 올바르지 않습니다: " + providerId);
            }
            return Integer.parseInt(providerId.substring(prefix.length()));
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
    }
}
