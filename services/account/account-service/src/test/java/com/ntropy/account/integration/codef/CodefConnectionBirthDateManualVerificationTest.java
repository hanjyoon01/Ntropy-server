package com.ntropy.account.integration.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.security.BirthDateCipher;
import com.ntropy.account.security.BirthDateCipher.EncryptedBirthDate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #158: 기업·국민은행 birthDate 저장 암호화가 실제 MySQL에서 (1) 평문을 남기지 않고,
 * (2) 저장된 값을 다시 복호화하면 원문과 같고, (3) birthDate가 필요 없는 은행을 추가 등록해도
 * 이전에 저장된 birthDate 암호문을 지우지 않는지(COALESCE 갱신) 검증한다.
 * CodefConnectionService 전체 흐름은 실제 CODEF 호출이 필요해 여기서는 Mapper/Cipher만으로
 * 저장 계층의 계약을 검증한다 (CODEF 등록 자체는 CodefConnectionManualVerificationTest가 다룬다).
 * RUN_DAILY_SYNC_LEASE_TEST=true일 때만 실행한다.
 */
class CodefConnectionBirthDateManualVerificationTest {

    private static final Long TEST_USER_ID = 9_999_999_158L;
    private static final String PLAIN_BIRTH_DATE = "19900101";

    @Test
    void birthDateCiphertextRoundTripsAndSurvivesUnrelatedBankAdditionAgainstRealMysql() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_DAILY_SYNC_LEASE_TEST")),
                "실제 MySQL이 필요한 이슈 #158 birthDate 암호화 저장 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = ctx.getBean(DataSource.class);
            ensureSchema(dataSource);
            CodefConnectionMapper mapper = ctx.getBean(CodefConnectionMapper.class);
            BirthDateCipher cipher = ctx.getBean(BirthDateCipher.class);

            cleanUp(dataSource);

            // 1) 국민은행(0004) 등록: birthDate 암호화해 저장.
            EncryptedBirthDate encrypted = cipher.encrypt(PLAIN_BIRTH_DATE);
            CodefConnection connection = new CodefConnection();
            connection.setUserId(TEST_USER_ID);
            connection.setProvider("CODEF");
            connection.setConnectedId("birth-date-test-connected-id");
            connection.setRegisteredInstitutionKeys("[\"0004\"]");
            connection.setBirthDateCiphertext(encrypted.ciphertextBase64());
            connection.setBirthDateIv(encrypted.ivBase64());
            connection.setBirthDateKeyVersion(encrypted.keyVersion());
            mapper.upsert(connection);

            // 2) DB에 평문이 남아있지 않은지 원시 SQL로 직접 확인.
            String rawCiphertext = queryColumn(dataSource, "birth_date_ciphertext");
            String rawIv = queryColumn(dataSource, "birth_date_iv");
            assertNotNull(rawCiphertext);
            assertFalse(rawCiphertext.contains(PLAIN_BIRTH_DATE), "저장된 암호문에 평문 생년월일이 남아있으면 안 됩니다");

            // 3) 저장된 값으로 복호화하면 원문과 같아야 한다.
            assertEquals(PLAIN_BIRTH_DATE, cipher.decrypt(rawCiphertext, rawIv));

            // 4) SC은행처럼 birthDate가 필요 없는 은행을 같은 커넥션에 추가 등록해도
            //    (upsert에 birth_date_* 없이) 기존 암호문이 지워지면 안 된다 (COALESCE).
            CodefConnection saved = mapper.findByUserIdAndProvider(TEST_USER_ID, "CODEF");
            saved.setRegisteredInstitutionKeys("[\"0004\",\"0023\"]");
            saved.setBirthDateCiphertext(null);
            saved.setBirthDateIv(null);
            saved.setBirthDateKeyVersion(null);
            mapper.upsert(saved);

            String ciphertextAfterUnrelatedUpdate = queryColumn(dataSource, "birth_date_ciphertext");
            String ivAfterUnrelatedUpdate = queryColumn(dataSource, "birth_date_iv");
            assertEquals(rawCiphertext, ciphertextAfterUnrelatedUpdate,
                    "birthDate가 필요 없는 은행 추가로 기존 birthDate 암호문이 지워지면 안 됩니다");
            assertEquals(PLAIN_BIRTH_DATE, cipher.decrypt(ciphertextAfterUnrelatedUpdate, ivAfterUnrelatedUpdate));

            cleanUp(dataSource);
        }
    }

    private static String queryColumn(DataSource dataSource, String column) throws Exception {
        String sql = "SELECT " + column + " FROM CODEF_CONNECTION WHERE user_id = ? AND provider = 'CODEF'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, TEST_USER_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "CODEF_CONNECTION 행을 찾을 수 없습니다");
                return resultSet.getString(1);
            }
        }
    }

    private static void cleanUp(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM CODEF_CONNECTION WHERE user_id = " + TEST_USER_ID);
        }
    }

    /** 로컬 DB가 아직 이슈 #158 migration을 적용하지 않았을 수 있어 컬럼을 idempotent하게 보장한다. */
    private static void ensureSchema(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            addColumnIfAbsent(statement,
                    "ALTER TABLE CODEF_CONNECTION ADD COLUMN birth_date_ciphertext VARCHAR(255) NULL");
            addColumnIfAbsent(statement,
                    "ALTER TABLE CODEF_CONNECTION ADD COLUMN birth_date_iv VARCHAR(32) NULL");
            addColumnIfAbsent(statement,
                    "ALTER TABLE CODEF_CONNECTION ADD COLUMN birth_date_key_version BIGINT NULL");
        }
    }

    private static void addColumnIfAbsent(Statement statement, String alterSql) throws SQLException {
        try {
            statement.execute(alterSql);
        } catch (SQLException e) {
            if (!"42S21".equals(e.getSQLState())) { // 컬럼이 이미 존재
                throw e;
            }
        }
    }

    @Configuration
    @ComponentScan(basePackages = "com.ntropy.account")
    @MapperScan("com.ntropy.account.mapper")
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:33306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
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
    }
}
