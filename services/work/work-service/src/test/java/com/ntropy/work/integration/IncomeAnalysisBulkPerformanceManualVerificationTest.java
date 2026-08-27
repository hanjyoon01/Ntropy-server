package com.ntropy.work.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.SettlementMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.service.IncomeAnalysisService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * IncomeAnalysisService의 단건 반복 조회(getMonthlyIncomeAnalysis를 사용자마다 호출)와
 * 벌크 조회(getMonthlyIncomeAnalysisBulk)를 실제 MySQL 기준으로 비교하는 수동 검증 테스트.
 *
 * <p>소요시간은 참고용으로만 출력한다(로컬 환경 노이즈에 흔들릴 수 있어 강하게 assert하지 않음).
 * 대신 QueryCountInterceptor로 센 실제 SQL 실행 횟수를 신뢰 가능한 증거로 삼아 assert한다 -
 * 벌크 경로는 사용자 수(PERF_TEST_USER_COUNT)와 무관하게 쿼리 6개로 고정되고, 루프 경로는
 * 사용자 수에 비례해서 늘어나야 한다.</p>
 *
 * <p>RUN_INCOME_ANALYSIS_BULK_PERF_TEST=true일 때만 실행한다.
 * 사용자 수는 PERF_TEST_USER_COUNT로 조절 가능(기본 300명).</p>
 */
class IncomeAnalysisBulkPerformanceManualVerificationTest {

    private static final long USER_ID_BASE = 9_777_000_000L;
    private static final long CATEGORY_ID = 999_999_001L;
    private static final long PLATFORM_ID = 999_999_001L;
    private static final YearMonth TARGET = YearMonth.of(2026, 7);
    private static final LocalDate TARGET_DATE = TARGET.atDay(10);
    private static final LocalDate PREVIOUS_MONTH_DATE = TARGET.minusMonths(1).atDay(10);

    @Test
    void bulkQueryUsesFixedQueryCountRegardlessOfUserCount() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_INCOME_ANALYSIS_BULK_PERF_TEST")),
                "실제 MySQL이 필요한 벌크 조회 성능 비교용 테스트"
        );

        int userCount = Integer.parseInt(System.getenv().getOrDefault("PERF_TEST_USER_COUNT", "300"));

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            List<Long> userIds = seedFixtures(dataSource, userCount);

            SettlementMapper settlementMapper = context.getBean(SettlementMapper.class);
            JobMapper jobMapper = context.getBean(JobMapper.class);
            WorkLogMapper workLogMapper = context.getBean(WorkLogMapper.class);
            WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper =
                    context.getBean(WorkLogPlatformIncomeMapper.class);
            IncomeAnalysisService service = new IncomeAnalysisService(
                    settlementMapper, jobMapper, workLogMapper, workLogPlatformIncomeMapper);
            QueryCountInterceptor queryCounter = context.getBean(QueryCountInterceptor.class);

            // 워밍업 - 첫 커넥션 획득/쿼리플랜 준비 비용이 측정치를 왜곡하지 않도록 한 번 흘려보낸다.
            service.getMonthlyIncomeAnalysis(userIds.get(0), TARGET);

            queryCounter.reset();
            long loopStartNanos = System.nanoTime();
            for (Long userId : userIds) {
                service.getMonthlyIncomeAnalysis(userId, TARGET);
            }
            long loopElapsedMs = (System.nanoTime() - loopStartNanos) / 1_000_000;
            int loopQueryCount = queryCounter.get();

            queryCounter.reset();
            long bulkStartNanos = System.nanoTime();
            service.getMonthlyIncomeAnalysisBulk(userIds, TARGET);
            long bulkElapsedMs = (System.nanoTime() - bulkStartNanos) / 1_000_000;
            int bulkQueryCount = queryCounter.get();

            System.out.println("===== IncomeAnalysis 성능 비교 (사용자 " + userCount + "명) =====");
            System.out.println("루프 방식 : " + loopElapsedMs + "ms, 쿼리 " + loopQueryCount + "회");
            System.out.println("벌크 방식 : " + bulkElapsedMs + "ms, 쿼리 " + bulkQueryCount + "회");
            System.out.println("=================================================");

            // 사용자 수와 무관하게 고정: SettlementMapper 3회(이번달/전월/전전월) + JobMapper 1회 +
            // WorkLogMapper 1회 + WorkLogPlatformIncomeMapper 1회 = 6회.
            assertTrue(bulkQueryCount <= 6, "벌크 경로 쿼리 수가 예상(6개)보다 많습니다: " + bulkQueryCount);
            // 루프 경로는 사용자 수만큼 곱해지므로 사용자 수 이상은 나와야 한다(§각 사용자당 최소 1쿼리).
            assertTrue(loopQueryCount >= userCount,
                    "루프 경로 쿼리 수가 사용자 수보다 적습니다 - N+1 패턴이 재현되지 않았을 수 있습니다");
            assertTrue(bulkQueryCount < loopQueryCount, "벌크 경로가 루프 경로보다 쿼리 수가 적어야 합니다");
        }
    }

    /** userCount명을 시딩하고 생성된 userId 목록을 돌려준다. */
    private List<Long> seedFixtures(DataSource dataSource, int userCount) throws Exception {
        List<Long> userIds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 이전 실행 잔여 데이터 정리 (같은 테스트 user_id 대역만).
            long userIdUpperBound = USER_ID_BASE + 100_000;
            statement.execute(
                    "DELETE FROM WORK_LOG_PLATFORM_INCOME WHERE log_id IN ("
                            + "SELECT log_id FROM WORK_LOG WHERE user_id BETWEEN " + USER_ID_BASE
                            + " AND " + userIdUpperBound + ")"
            );
            statement.execute(
                    "DELETE FROM WORK_LOG WHERE user_id BETWEEN " + USER_ID_BASE + " AND " + userIdUpperBound
            );
            statement.execute(
                    "DELETE FROM SETTLEMENT WHERE user_id BETWEEN " + USER_ID_BASE + " AND " + userIdUpperBound
            );
            statement.execute(
                    "DELETE FROM JOB WHERE user_id BETWEEN " + USER_ID_BASE + " AND " + userIdUpperBound
            );

            statement.execute(
                    "INSERT IGNORE INTO CATEGORY (category_id, name) VALUES ("
                            + CATEGORY_ID + ", '성능테스트카테고리')"
            );
            statement.execute(
                    "INSERT IGNORE INTO PLATFORM (platform_id, category_id, platform_name, deposit_name, "
                            + "settlement_cycle) VALUES (" + PLATFORM_ID + ", " + CATEGORY_ID
                            + ", '성능테스트플랫폼', '성능테스트입금처', 'MONTHLY')"
            );

            for (int i = 0; i < userCount; i++) {
                long userId = USER_ID_BASE + i;
                userIds.add(userId);

                statement.execute(
                        "INSERT INTO JOB (user_id, category_id, job_name, settlement_type, is_regular, "
                                + "base_fatigue, created_at, updated_at, is_active) VALUES ("
                                + userId + ", " + CATEGORY_ID + ", '성능테스트잡', 'MONTHLY', true, 3, "
                                + "NOW(), NOW(), true)"
                );
                long jobId = lastInsertId(statement);

                statement.execute(
                        "INSERT INTO SETTLEMENT (user_id, status, job_id, period_start, period_end, "
                                + "deposit_date, expected_amount, actual_amount, transaction_count, matched_at) "
                                + "VALUES (" + userId + ", 'MATCHED', " + jobId + ", '" + TARGET_DATE + "', '"
                                + TARGET_DATE + "', '" + TARGET_DATE + "', 0, 100000, 1, NOW())"
                );
                statement.execute(
                        "INSERT INTO SETTLEMENT (user_id, status, job_id, period_start, period_end, "
                                + "deposit_date, expected_amount, actual_amount, transaction_count, matched_at) "
                                + "VALUES (" + userId + ", 'MATCHED', " + jobId + ", '" + PREVIOUS_MONTH_DATE
                                + "', '" + PREVIOUS_MONTH_DATE + "', '" + PREVIOUS_MONTH_DATE
                                + "', 0, 90000, 1, NOW())"
                );

                statement.execute(
                        "INSERT INTO WORK_LOG (user_id, job_id, work_date, start_time, end_time, fatigue, "
                                + "estimated_income, status, settlement_status) VALUES (" + userId + ", " + jobId
                                + ", '" + TARGET_DATE + "', '09:00:00', '18:00:00', 3, 100000, 'CONFIRMED', "
                                + "'PENDING')"
                );
                long logId = lastInsertId(statement);

                statement.execute(
                        "INSERT INTO WORK_LOG_PLATFORM_INCOME (log_id, platform_id, expected_amount, "
                                + "settlement_status) VALUES (" + logId + ", " + PLATFORM_ID + ", 100000, "
                                + "'PENDING')"
                );
            }
        }
        return userIds;
    }

    private long lastInsertId(Statement statement) throws Exception {
        try (var rs = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Executor에 들어오는 query/update 호출 횟수를 세는 MyBatis 플러그인 - 실제 SQL 실행 횟수의 대용치. */
    @Intercepts({
            @Signature(type = Executor.class, method = "query",
                    args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class,
                            org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class}),
            @Signature(type = Executor.class, method = "update",
                    args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class})
    })
    static class QueryCountInterceptor implements Interceptor {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            count.incrementAndGet();
            return invocation.proceed();
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }

        void reset() {
            count.set(0);
        }

        int get() {
            return count.get();
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {
            SettlementMapper.class, JobMapper.class, WorkLogMapper.class, WorkLogPlatformIncomeMapper.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(System.getenv().getOrDefault(
                    "WORK_TEST_DB_URL",
                    "jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            ));
            config.setUsername(System.getenv().getOrDefault("WORK_TEST_DB_USERNAME", "root"));
            config.setPassword(System.getenv().getOrDefault("WORK_TEST_DB_PASSWORD", "root"));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(10);
            return new HikariDataSource(config);
        }

        @Bean
        QueryCountInterceptor queryCountInterceptor() {
            return new QueryCountInterceptor();
        }

        @Bean
        SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource, QueryCountInterceptor queryCountInterceptor)
                throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setMapperLocations(
                    new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")
            );
            factoryBean.setPlugins(queryCountInterceptor);
            return factoryBean;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
