package com.ntropy.account.integration.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.ntropy.account.client.codef.CodefOAuthClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 실제 CODEF 서버(oauth.codef.io)와 로컬 MySQL(docker)에 붙어서
 * 토큰 발급 -> DB 저장 -> 캐시 재사용 왕복을 확인하는 수동 검증용 테스트.
 * 외부 네트워크/로컬 DB에 의존하므로 RUN_CODEF_MANUAL_TEST=true일 때만 실행한다.
 * PowerShell 실행 예: $env:RUN_CODEF_MANUAL_TEST='true'; ./gradlew :services:account-service:test --tests "*CodefOAuthClientManualVerificationTest"
 */
class CodefOAuthClientManualVerificationTest {

    @Test
    void issuesTokenThenReusesCachedTokenFromDb() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_CODEF_MANUAL_TEST")),
                "외부 네트워크(CODEF) + 로컬 MySQL이 필요한 수동 검증용 테스트"
        );
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            CodefOAuthClient client = ctx.getBean(CodefOAuthClient.class);

            String firstToken = client.getValidAccessToken();
            assertNotNull(firstToken);

            String secondToken = client.getValidAccessToken();
            assertEquals(firstToken, secondToken, "캐시된 토큰이 재사용되지 않고 새로 발급됨");
        }
    }

    @Configuration
    @ComponentScan(basePackages = "com.ntropy.account")
    @MapperScan("com.ntropy.account.mapper")
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
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
