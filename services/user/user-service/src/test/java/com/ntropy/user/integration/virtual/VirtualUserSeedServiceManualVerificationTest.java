package com.ntropy.user.integration.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.ntropy.user.api.dto.VirtualUserDataset;
import com.ntropy.user.config.VirtualTestProperties;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.service.VirtualUserSeedService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** RUN_VIRTUAL_USER_SEED_TEST=true일 때 로컬 MySQL에 가상회원을 멱등 시딩한다 (이슈 #166). */
class VirtualUserSeedServiceManualVerificationTest {

    private static final int USER_COUNT = 50;

    @Test
    void seedsVirtualUsersIdempotently() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_VIRTUAL_USER_SEED_TEST")),
                "로컬 MySQL에 가상회원을 시딩하는 수동 검증 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            VirtualUserSeedService service = context.getBean(VirtualUserSeedService.class);
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            service.seed();
            service.seed();

            assertEquals(USER_COUNT, count(jdbc));

            VirtualUserDataset dataset = service.findSeededVirtualUsers();
            assertEquals(USER_COUNT, dataset.users().size());
            assertEquals(USER_COUNT, dataset.users().stream().map(user -> user.userId()).distinct().count());
            assertEquals(
                    List.of(1, USER_COUNT),
                    List.of(
                            dataset.users().stream().mapToInt(user -> user.ordinal()).min().orElseThrow(),
                            dataset.users().stream().mapToInt(user -> user.ordinal()).max().orElseThrow()
                    )
            );
        }
    }

    @Test
    void concurrentSeedingDoesNotCreateDuplicates() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_VIRTUAL_USER_SEED_TEST")),
                "로컬 MySQL에 가상회원을 시딩하는 수동 검증 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            VirtualUserSeedService service = context.getBean(VirtualUserSeedService.class);
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        service.seed();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("동시 시딩 대기 중 인터럽트되었습니다.", e);
                    }
                }));
            }
            ready.await();
            start.countDown();
            executor.shutdown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "동시 시딩 작업이 제한 시간 안에 끝나야 합니다");

            assertEquals(USER_COUNT, count(jdbc));
        }
    }

    private static int count(JdbcTemplate jdbc) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM USERS WHERE provider = 'NTROPY_TEST'", Integer.class);
        return value == null ? 0 : value;
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
                    true, USER_COUNT, 20260817L, "2026-08-17", "VIRTUAL-MULTI-DOMAIN-v1", "local"
            );
        }

        @Bean
        VirtualUserSeedService virtualUserSeedService(UserMapper userMapper, VirtualTestProperties properties) {
            return new VirtualUserSeedService(userMapper, properties);
        }
    }
}
