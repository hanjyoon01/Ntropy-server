package com.ntropy.account.integration.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

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

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.VirtualAccountService;
import com.ntropy.account.service.VirtualConnectionService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 로컬 MySQL에 가상 연결과 계좌를 실제로 저장하고 동일 요청의 중복 방지를 확인하는 수동 검증 테스트.
 * 외부 금융기관이나 CODEF API를 호출하지 않으며 RUN_VIRTUAL_ACCOUNT_TEST=true일 때만 실행한다.
 */
class VirtualAccountManualVerificationTest {

    private static final Long TEST_USER_ID = 9_000_000_035L;
    private static final String TEST_ORGANIZATION_CODE = "0088";

    @Test
    void createsVirtualConnectionAndAccountWithoutDuplicates() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_VIRTUAL_ACCOUNT_TEST")),
                "로컬 MySQL이 필요한 가상계좌 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            VirtualAccountService service = context.getBean(VirtualAccountService.class);

            Account first = service.createVirtualAccount(TEST_USER_ID, TEST_ORGANIZATION_CODE);
            Account second = service.createVirtualAccount(TEST_USER_ID, TEST_ORGANIZATION_CODE);

            assertNotNull(first.getId());
            assertEquals(first.getId(), second.getId(), "동일 요청으로 계좌가 중복 생성됨");
            assertEquals(first.getAccountNoHash(), second.getAccountNoHash());

            CodefConnectionMapper connectionMapper = context.getBean(CodefConnectionMapper.class);
            CodefConnection connection = connectionMapper.findByUserIdAndProvider(
                    TEST_USER_ID, ConnectionProvider.NTROPY.name()
            );
            assertNotNull(connection);
            assertTrue(connection.getConnectedId().startsWith("NTROPY-"));
            assertTrue(
                    InstitutionKeys.parse(connection.getRegisteredInstitutionKeys())
                            .contains(TEST_ORGANIZATION_CODE)
            );

            AccountMapper accountMapper = context.getBean(AccountMapper.class);
            List<Account> accounts = accountMapper.findByUserIdAndProvider(
                    TEST_USER_ID, ConnectionProvider.NTROPY.name()
            );
            assertEquals(1, accounts.size());
            assertEquals(first.getId(), accounts.get(0).getId());

            System.out.println("VIRTUAL_TEST_USER_ID=" + TEST_USER_ID);
            System.out.println("VIRTUAL_ORGANIZATION_CODE=" + TEST_ORGANIZATION_CODE);
            System.out.println("VIRTUAL_CONNECTION_ID=" + connection.getId());
            System.out.println("VIRTUAL_ACCOUNT_ID=" + first.getId());
            System.out.println("VIRTUAL_ACCOUNT_COUNT=" + accounts.size());
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {AccountMapper.class, CodefConnectionMapper.class})
    static class TestConfig {

        @Bean
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
        VirtualConnectionService virtualConnectionService(CodefConnectionMapper connectionMapper) {
            return new VirtualConnectionService(connectionMapper);
        }

        @Bean
        VirtualAccountService virtualAccountService(
                VirtualConnectionService connectionService,
                AccountMapper accountMapper
        ) {
            return new VirtualAccountService(connectionService, accountMapper);
        }
    }
}
