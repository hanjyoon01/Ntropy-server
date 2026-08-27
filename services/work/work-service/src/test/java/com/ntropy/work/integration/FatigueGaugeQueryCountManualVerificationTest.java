package com.ntropy.work.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.mapper.SavingGoalMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.service.FatigueService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * FatigueService.calculateGauge()의 개선 전(하루씩 7번 조회)과 개선 후(범위 1번 벌크 조회)의
 * 쿼리 수/소요시간 차이를 실제 MySQL 기준으로 비교하는 수동 검증 테스트.
 *
 * <p>개선 전 방식은 이미 프로덕션 코드에서 제거됐으므로, 옛 호출 패턴(findByUserIdAndWorkDate를
 * 7번 호출)을 이 테스트 안에서 그대로 재현해 비교 기준선으로 삼는다. WorkLogMapper 자체는
 * 다른 서비스(WorkLogService 등)에서 여전히 쓰이고 있어 그대로 남아있다.</p>
 *
 * <p>이 최적화는 IncomeAnalysis 벌크 조회(배치 1회 실행에서 사용자 수와 무관하게 쿼리 수 고정)와는
 * 성격이 다르다 - calculateGauge는 캘린더 일간 조회 1건당 호출되는 요청 단위 로직이라, 사용자 수(N)가
 * 늘어도 두 방식 다 쿼리 수는 N에 비례해서 늘어난다. 다만 사용자 1명당 쿼리 수가 8회 -> 2회로
 * 줄어드는 것이므로, 총 쿼리 수는 N에 관계없이 항상 4배 차이가 난다.</p>
 *
 * <p>소요시간은 참고용으로만 출력한다(로컬 환경 노이즈에 흔들릴 수 있어 강하게 assert하지 않음).
 * 대신 QueryCountInterceptor로 센 실제 SQL 실행 횟수를 신뢰 가능한 증거로 삼아 assert한다.</p>
 *
 * <p>RUN_FATIGUE_PERF_TEST=true일 때만 실행한다.
 * 사용자(=캘린더 일간 조회 요청) 수는 PERF_TEST_USER_COUNT로 조절 가능(기본 300건).</p>
 */
class FatigueGaugeQueryCountManualVerificationTest {

    private static final long USER_ID_BASE = 9_888_000_000L;
    private static final long CATEGORY_ID = 999_999_002L;
    private static final YearMonth TARGET_MONTH = YearMonth.of(2026, 8);
    private static final LocalDate TARGET_DATE = TARGET_MONTH.atDay(10);

    /** 개선 전 FatigueService가 실제로 실행하던 쿼리 수: 하루씩 WINDOW_DAYS(7)번 + SAVING_GOAL 조회 1번. */
    private static final int OLD_STYLE_QUERY_COUNT_PER_USER = FatigueService.WINDOW_DAYS + 1;
    /** 개선 후: 범위 조회 1번 + SAVING_GOAL 조회 1번. */
    private static final int NEW_STYLE_QUERY_COUNT_PER_USER = 2;

    @Test
    void bulkRangeQueryUsesFewerQueriesThanOldPerDayLoop() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_FATIGUE_PERF_TEST")),
                "실제 MySQL이 필요한 FatigueService 쿼리 수 비교용 테스트"
        );

        int userCount = Integer.parseInt(System.getenv().getOrDefault("PERF_TEST_USER_COUNT", "300"));

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            List<Long> userIds = seedFixtures(dataSource, userCount);

            WorkLogMapper workLogMapper = context.getBean(WorkLogMapper.class);
            SavingGoalMapper savingGoalMapper = context.getBean(SavingGoalMapper.class);
            FatigueService fatigueService = new FatigueService(savingGoalMapper);
            QueryCountInterceptor queryCounter = context.getBean(QueryCountInterceptor.class);

            // 워밍업 - 첫 커넥션 획득/쿼리플랜 준비 비용이 측정치를 왜곡하지 않도록 한 번 흘려보낸다.
            oldStyleFatigueQueries(workLogMapper, savingGoalMapper, userIds.get(0));

            queryCounter.reset();
            long oldStartNanos = System.nanoTime();
            for (Long userId : userIds) {
                oldStyleFatigueQueries(workLogMapper, savingGoalMapper, userId);
            }
            long oldElapsedMs = (System.nanoTime() - oldStartNanos) / 1_000_000;
            int oldQueryCount = queryCounter.get();

            queryCounter.reset();
            long newStartNanos = System.nanoTime();
            for (Long userId : userIds) {
                List<WorkLog> workLogs = workLogMapper.findByUserIdAndDateRange(
                        userId, TARGET_DATE.minusDays(FatigueService.WINDOW_DAYS - 1L), TARGET_DATE);
                fatigueService.calculateGauge(userId, TARGET_DATE, workLogs);
            }
            long newElapsedMs = (System.nanoTime() - newStartNanos) / 1_000_000;
            int newQueryCount = queryCounter.get();

            System.out.println("===== FatigueService 게이지 계산 쿼리 수 비교 (요청 " + userCount + "건) =====");
            System.out.println("개선 전(하루씩 루프) : " + oldElapsedMs + "ms, 쿼리 " + oldQueryCount + "회");
            System.out.println("개선 후(범위 벌크)   : " + newElapsedMs + "ms, 쿼리 " + newQueryCount + "회");
            System.out.println("=======================================================");

            assertEquals(OLD_STYLE_QUERY_COUNT_PER_USER * userCount, oldQueryCount,
                    "개선 전 방식의 쿼리 수가 예상(요청당 " + OLD_STYLE_QUERY_COUNT_PER_USER + "회)과 다릅니다");
            assertEquals(NEW_STYLE_QUERY_COUNT_PER_USER * userCount, newQueryCount,
                    "개선 후 방식의 쿼리 수가 예상(요청당 " + NEW_STYLE_QUERY_COUNT_PER_USER + "회)과 다릅니다");
            assertTrue(newQueryCount < oldQueryCount, "개선 후 방식이 개선 전보다 쿼리 수가 적어야 합니다");
        }
    }

    /** 개선 전 FatigueService.calculateGauge()가 실행하던 쿼리 패턴을 그대로 재현한다. */
    private void oldStyleFatigueQueries(WorkLogMapper workLogMapper, SavingGoalMapper savingGoalMapper, Long userId) {
        for (int n = 0; n < FatigueService.WINDOW_DAYS; n++) {
            workLogMapper.findByUserIdAndWorkDate(userId, TARGET_DATE.minusDays(n));
        }
        savingGoalMapper.findByUserIdAndTargetMonth(userId, TARGET_MONTH.toString());
    }

    /** userCount명을 시딩하고 생성된 userId 목록을 돌려준다. */
    private List<Long> seedFixtures(DataSource dataSource, int userCount) throws Exception {
        List<Long> userIds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 이전 실행 잔여 데이터 정리 (같은 테스트 user_id 대역만).
            long userIdUpperBound = USER_ID_BASE + 100_000;
            statement.execute(
                    "DELETE FROM WORK_LOG WHERE user_id BETWEEN " + USER_ID_BASE + " AND " + userIdUpperBound
            );
            statement.execute(
                    "DELETE FROM JOB WHERE user_id BETWEEN " + USER_ID_BASE + " AND " + userIdUpperBound
            );

            statement.execute(
                    "INSERT IGNORE INTO CATEGORY (category_id, name) VALUES ("
                            + CATEGORY_ID + ", '성능테스트카테고리')"
            );

            for (int i = 0; i < userCount; i++) {
                long userId = USER_ID_BASE + i;
                userIds.add(userId);

                statement.execute(
                        "INSERT INTO JOB (user_id, category_id, job_name, settlement_type, is_regular, "
                                + "base_fatigue, created_at, updated_at, is_active) VALUES ("
                                + userId + ", " + CATEGORY_ID + ", '성능테스트잡', 'HOURLY', false, 3, "
                                + "NOW(), NOW(), true)"
                );
                long jobId = lastInsertId(statement);

                statement.execute(
                        "INSERT INTO WORK_LOG (user_id, job_id, work_date, start_time, end_time, fatigue, "
                                + "estimated_income, status, settlement_status) VALUES (" + userId + ", " + jobId
                                + ", '" + TARGET_DATE + "', '09:00:00', '18:00:00', 3, 100000, 'CONFIRMED', "
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
    @MapperScan(basePackageClasses = {WorkLogMapper.class, SavingGoalMapper.class})
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
