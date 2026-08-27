package com.ntropy.account.integration.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

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

import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.AccountNoMask;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #84로 추가한 ACCOUNT.loan_contract_principal/interest_rate/maturity_date의
 * nullable 저장·재조회와 interest_rate 고정금리 비덮어쓰기 정책을 로컬 MySQL로 검증하는
 * 수동 검증 테스트. RUN_ACCOUNT_LOAN_COLUMNS_TEST=true일 때만 실행한다.
 */
class AccountLoanColumnsManualVerificationTest {

    private static final Long TEST_USER_ID = 9_000_000_084L;
    private static final String TEST_ORGANIZATION_CODE = "0088";
    private static final String TEST_RAW_ACCOUNT_NO = "999888777666";

    @Test
    void preservesNullsAndKeepsFixedInterestRateOnResync() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_ACCOUNT_LOAN_COLUMNS_TEST")),
                "로컬 MySQL이 필요한 대출·예적금 컬럼 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            CodefConnectionMapper connectionMapper = context.getBean(CodefConnectionMapper.class);
            AccountMapper accountMapper = context.getBean(AccountMapper.class);

            CodefConnection connection = new CodefConnection();
            connection.setUserId(TEST_USER_ID);
            connection.setProvider("CODEF");
            connection.setConnectedId("codef-loan-column-test");
            connectionMapper.upsert(connection);
            connection = connectionMapper.findByUserIdAndProvider(TEST_USER_ID, "CODEF");
            assertNotNull(connection);

            String accountNoHash = AccountNoHash.hash(TEST_ORGANIZATION_CODE, TEST_RAW_ACCOUNT_NO);

            Account loanAccount = new Account();
            loanAccount.setCodefConnectionId(connection.getId());
            loanAccount.setUserId(TEST_USER_ID);
            loanAccount.setOrganizationCode(TEST_ORGANIZATION_CODE);
            loanAccount.setAccountGroup(AccountGroup.LOAN);
            loanAccount.setDepositTypeCode("40");
            loanAccount.setAccountNoMasked(AccountNoMask.mask(TEST_RAW_ACCOUNT_NO));
            loanAccount.setAccountNoHash(accountNoHash);
            loanAccount.setBalance(new BigDecimal("5000000.00"));
            loanAccount.setLoanContractPrincipal(new BigDecimal("10000000.00"));
            loanAccount.setInterestRate(new BigDecimal("3.5000"));
            loanAccount.setMaturityDate(LocalDate.of(2030, 1, 1));
            loanAccount.setCurrencyCode("KRW");

            accountMapper.upsert(loanAccount);

            Account first = accountMapper.findByConnectionIdAndAccountNoHash(connection.getId(), accountNoHash);
            assertNotNull(first);
            assertEquals(0, new BigDecimal("10000000.00").compareTo(first.getLoanContractPrincipal()));
            assertEquals(0, new BigDecimal("3.5000").compareTo(first.getInterestRate()));
            assertEquals(LocalDate.of(2030, 1, 1), first.getMaturityDate());
            assertNull(first.getNextPaymentDate(), "설정하지 않은 nullable 컬럼은 null로 보존돼야 한다");

            // 재동기화 시 약정원금은 갱신되지만, 이미 저장된 고정금리는 자동으로 덮어쓰지 않는다.
            Account resynced = new Account();
            resynced.setCodefConnectionId(connection.getId());
            resynced.setUserId(TEST_USER_ID);
            resynced.setOrganizationCode(TEST_ORGANIZATION_CODE);
            resynced.setAccountGroup(AccountGroup.LOAN);
            resynced.setDepositTypeCode("40");
            resynced.setAccountNoMasked(AccountNoMask.mask(TEST_RAW_ACCOUNT_NO));
            resynced.setAccountNoHash(accountNoHash);
            resynced.setBalance(new BigDecimal("4500000.00"));
            resynced.setLoanContractPrincipal(new BigDecimal("12000000.00"));
            resynced.setInterestRate(new BigDecimal("5.0000"));
            resynced.setCurrencyCode("KRW");

            accountMapper.upsert(resynced);

            Account second = accountMapper.findByConnectionIdAndAccountNoHash(connection.getId(), accountNoHash);
            assertEquals(0, new BigDecimal("12000000.00").compareTo(second.getLoanContractPrincipal()),
                    "약정원금은 재동기화 값으로 갱신돼야 한다");
            assertEquals(0, new BigDecimal("3.5000").compareTo(second.getInterestRate()),
                    "이미 저장된 고정금리는 재동기화로 자동 변경되지 않아야 한다");
            assertEquals(LocalDate.of(2030, 1, 1), second.getMaturityDate(),
                    "새 값이 없으면 기존 만기일을 유지해야 한다");
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
    }
}
