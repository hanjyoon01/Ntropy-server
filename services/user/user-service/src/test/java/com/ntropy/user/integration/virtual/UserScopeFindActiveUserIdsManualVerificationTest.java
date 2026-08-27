package com.ntropy.user.integration.virtual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ntropy.user.config.VirtualTestProperties;
import com.ntropy.user.domain.entity.User;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.service.VirtualUserSeedService;
import com.ntropy.user.virtual.VirtualUserProviderId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * RUN_USER_SCOPE_TEST=true일 때 로컬 MySQL에 실사용자 1명 + 가상회원(NTROPY_TEST)을 함께 저장한 뒤,
 * UserMapper.findActiveUserIds(scope, ...)가 REAL_ONLY/VIRTUAL_ONLY/ALL을 실제로 구분해 조회하는지
 * 검증한다 (이슈 #167). 코드 리뷰에서 요구된 실제 DB 검증 시나리오를 담당하며, 문자열 매칭만 하는
 * mapper XML 계약 테스트(UserMapperUserScopeContractTest)와는 목적이 다르다.
 */
class UserScopeFindActiveUserIdsManualVerificationTest {

    private static final int VIRTUAL_USER_COUNT = 3;
    private static final String REAL_TEST_PROVIDER = "MANUAL_TEST_REAL";
    private static final String REAL_TEST_PROVIDER_ID = "user-scope-manual-test";

    private AnnotationConfigApplicationContext context;
    private UserMapper userMapper;
    private Long realTestUserId;

    @AfterEach
    void cleanUpRealTestUser() {
        // 가상회원은 다른 수동 검증 테스트와 동일하게 멱등 시딩 상태로 남겨두지만, 이 테스트가 만든
        // "실사용자" 행은 로컬 DB에 계속 남아 다음 실행의 REAL_ONLY 대상에 잘못 섞이지 않도록 정리한다.
        if (userMapper != null && realTestUserId != null) {
            userMapper.deleteUser(realTestUserId);
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void realOnlyVirtualOnlyAndAllScopesFilterSeededUsersCorrectly() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_USER_SCOPE_TEST")),
                "로컬 MySQL에서 배치 user-scope 필터링을 검증하는 수동 검증 테스트"
        );

        context = new AnnotationConfigApplicationContext(TestConfig.class);
        userMapper = context.getBean(UserMapper.class);
        VirtualUserSeedService virtualUserSeedService = context.getBean(VirtualUserSeedService.class);

        virtualUserSeedService.seed();
        List<Long> virtualUserIds = virtualUserSeedService.findSeededVirtualUsers().users().stream()
                .map(identity -> identity.userId())
                .toList();
        assertTrue(virtualUserIds.size() >= VIRTUAL_USER_COUNT, "가상회원이 시딩돼 있어야 합니다");

        realTestUserId = insertRealTestUser(userMapper);

        List<Long> realOnly = userMapper.findActiveUserIds("REAL_ONLY", VirtualUserProviderId.PROVIDER);
        assertTrue(realOnly.contains(realTestUserId), "REAL_ONLY는 실사용자를 포함해야 합니다");
        assertTrue(
                virtualUserIds.stream().noneMatch(realOnly::contains),
                "REAL_ONLY는 가상회원(NTROPY_TEST)을 하나도 포함하면 안 됩니다"
        );

        List<Long> virtualOnly = userMapper.findActiveUserIds("VIRTUAL_ONLY", VirtualUserProviderId.PROVIDER);
        assertTrue(virtualOnly.containsAll(virtualUserIds), "VIRTUAL_ONLY는 시딩된 가상회원을 전부 포함해야 합니다");
        assertFalse(virtualOnly.contains(realTestUserId), "VIRTUAL_ONLY는 실사용자를 포함하면 안 됩니다");

        List<Long> all = userMapper.findActiveUserIds("ALL", VirtualUserProviderId.PROVIDER);
        assertTrue(all.contains(realTestUserId), "ALL은 실사용자를 포함해야 합니다");
        assertTrue(all.containsAll(virtualUserIds), "ALL은 가상회원도 포함해야 합니다");
    }

    /** (provider, provider_id) unique key 덕분에 재실행해도 새 행이 아니라 같은 행으로 수렴한다. */
    private static Long insertRealTestUser(UserMapper userMapper) {
        User user = new User();
        user.setProvider(REAL_TEST_PROVIDER);
        user.setProviderId(REAL_TEST_PROVIDER_ID);
        user.setName("user-scope 수동 검증용 실사용자");
        user.setEmail(REAL_TEST_PROVIDER_ID + "@ntropy.test");
        user.setStatus("ACTIVE");
        user.setRole("ROLE_USER");
        user.setTermsAgreed(true);
        user.setOnboardingCompleted(true);

        userMapper.insertUser(user);
        return userMapper.findByProviderAndProviderId(REAL_TEST_PROVIDER, REAL_TEST_PROVIDER_ID)
                .orElseThrow(() -> new IllegalStateException("실사용자 테스트 행을 저장하지 못했습니다"))
                .getUserId();
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = UserMapper.class)
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
        VirtualTestProperties virtualTestProperties() {
            return new VirtualTestProperties(
                    true, VIRTUAL_USER_COUNT, 20260817L, "2026-08-17", "VIRTUAL-MULTI-DOMAIN-v1", "local"
            );
        }

        @Bean
        VirtualUserSeedService virtualUserSeedService(UserMapper userMapper, VirtualTestProperties properties) {
            return new VirtualUserSeedService(userMapper, properties);
        }
    }
}
