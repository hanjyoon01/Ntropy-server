package com.ntropy.work.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;

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

import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.WorkLogMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * WorkLogMapper.xml의 update 쿼리에 job_id가 SET 절에서 빠져있던 버그(수정 시 job_id
 * 변경이 DB에 반영 안 됨) 회귀 테스트. InMemoryWorkLogMapper는 객체를 통째로 저장하는
 * 방식이라 이 컬럼 누락이 유닛 테스트로는 잡히지 않아 real MySQL로 검증한다.
 *
 * <p>RUN_WORKLOG_MAPPER_TEST=true일 때만 실행한다.</p>
 */
class WorkLogMapperUpdateJobIdManualVerificationTest {

    private static final long USER_ID = 9_000_000_163L;
    private static final long CATEGORY_ID = 999_999_163L;

    @Test
    void update_persistsChangedJobId() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_WORKLOG_MAPPER_TEST")),
                "로컬 MySQL이 필요한 WorkLogMapper.update job_id 반영 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            long jobAId;
            long jobBId;

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DELETE FROM WORK_LOG WHERE user_id = " + USER_ID);
                statement.execute("DELETE FROM JOB WHERE user_id = " + USER_ID);
                statement.execute(
                        "INSERT IGNORE INTO CATEGORY (category_id, name) VALUES (" + CATEGORY_ID + ", '테스트카테고리')"
                );
                jobAId = insertJob(statement, "잡A");
                jobBId = insertJob(statement, "잡B");
            }

            WorkLogMapper workLogMapper = context.getBean(WorkLogMapper.class);

            WorkLog workLog = WorkLog.builder()
                    .userId(USER_ID)
                    .jobId(jobAId)
                    .workDate(LocalDate.of(2026, 8, 26))
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .fatigue(3L)
                    .estimatedIncome(90000L)
                    .status("PLANNED")
                    .settlementStatus(SettlementStatus.NONE)
                    .build();
            workLogMapper.insert(workLog);

            workLog.setJobId(jobBId);
            workLog.setEstimatedIncome(120000L);
            workLogMapper.update(workLog);

            WorkLog reloaded = workLogMapper.findById(workLog.getLogId());
            assertEquals(jobBId, reloaded.getJobId(), "수정된 job_id가 DB에 반영돼야 한다");
            assertEquals(120000L, reloaded.getEstimatedIncome());
        }
    }

    private long insertJob(Statement statement, String jobName) throws Exception {
        statement.execute(
                "INSERT INTO JOB (user_id, category_id, job_name, settlement_type, is_regular, "
                        + "base_fatigue, created_at, updated_at, is_active) VALUES ("
                        + USER_ID + ", " + CATEGORY_ID + ", '" + jobName + "', 'HOURLY', false, 3, "
                        + "NOW(), NOW(), true)"
        );
        try (var rs = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = WorkLogMapper.class)
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
