package com.ntropy.account.integration.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

import com.ntropy.account.api.dto.CategoryExpenseAmount;
import com.ntropy.account.mapper.FinancialCommitmentMapper;
import com.ntropy.account.mapper.MonthlyExpenseMapper;
import com.ntropy.account.mapper.projection.LoanCommitmentCandidateRow;
import com.ntropy.account.api.domain.LoanDisbursementKeywords;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #143/#148/#169 최종: LOAN·INSTALLMENT의 소비 집계가 ORDINARY와 동일하게
 * TXN_ANALYSIS(is_consumption/category/expense_type)를 그대로 신뢰하고, 금액만 거래
 * 유형별 원천(LOAN은 out_amount, INSTALLMENT는 in_amount)에서 가져오는지 검증하는 수동
 * 테스트입니다. 지급 판정 로직 자체는 더 이상 이 매퍼의 관심사가 아니며(ai-service
 * TransactionPreClassificationService, #148의 책임), FinancialCommitmentMapper의 "최근
 * 정상 상환 선택" 로직만 별도로 지급 거래 키워드를 계속 사용하므로 그 부분만 함께 검증한다.
 * {@code MonthlyExpenseMapperContractTest}는 XML 텍스트만 확인하므로 CASE/INNER JOIN 같은
 * 실제 SQL 문법 오류나 계산값 자체는 여기서만 잡을 수 있습니다.
 * RUN_MONTHLY_EXPENSE_LOAN_RULE_TEST=true일 때만 실행합니다.
 */
class MonthlyExpenseMapperLoanRuleManualVerificationTest {

    private static final Long USER_ID = 9_999_999_143L;
    private static final YearMonth TARGET_MONTH = YearMonth.of(2031, 1);

    @Test
    void loanAndInstallmentConsumptionFollowsTxnAnalysisJustLikeOrdinary() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_MONTHLY_EXPENSE_LOAN_RULE_TEST")),
                "실제 MySQL이 필요한 LOAN/INSTALLMENT 소비 규칙 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            seedFixtures(dataSource);

            MonthlyExpenseMapper mapper = context.getBean(MonthlyExpenseMapper.class);
            FinancialCommitmentMapper financialCommitmentMapper =
                    context.getBean(FinancialCommitmentMapper.class);
            LocalDate startDate = TARGET_MONTH.atDay(1);
            LocalDate endDate = TARGET_MONTH.plusMonths(1).atDay(1);

            Long totalExpense = mapper.findTotalExpense(USER_ID, startDate, endDate);
            Long fixedExpense = mapper.findFixedExpense(USER_ID, startDate, endDate);
            List<CategoryExpenseAmount> categoryRows =
                    mapper.findCategoryExpenses(USER_ID, startDate, endDate);
            Map<String, Long> categories = categoryRows.stream()
                    .collect(Collectors.toMap(CategoryExpenseAmount::getCategory, CategoryExpenseAmount::getExpenseAmount));

            // T1(ORDINARY FOOD/VARIABLE 50,000) + T2(ORDINARY HOUSING/FIXED 200,000)
            // + T3(ORDINARY 비소비, 제외)
            // + T4(LOAN, TXN_ANALYSIS TRUE/FINANCE/FIXED, out_amount 300,000 전액 반영 -
            //     원금 포함, #169 최종 정책)
            // + T5(LOAN, TXN_ANALYSIS FALSE/NULL/NULL - ai-service가 이미 지급 거래로
            //     판정해둔 상태, out_amount가 있어도 제외)
            // + T6(LOAN, TXN_ANALYSIS 없음 - 배치 전이라 ORDINARY처럼 그 달 집계에서 제외)
            // + T7(INSTALLMENT, TXN_ANALYSIS TRUE/FINANCE/FIXED, in_amount 150,000 반영 -
            //     out_amount는 0이라 무시됨)
            // + T8(INSTALLMENT, TXN_ANALYSIS 없음 - 제외)
            // + T9(대상월 밖 LOAN, TXN_ANALYSIS 있어도 날짜 필터로 제외)
            // LOAN+INSTALLMENT 합계 = T4(300,000) + T7(150,000) = 450,000
            assertEquals(700_000L, totalExpense,
                    "총소비: LOAN/INSTALLMENT는 TXN_ANALYSIS.is_consumption을 그대로 따르고, "
                            + "금액은 LOAN=out_amount·INSTALLMENT=in_amount여야 합니다");
            assertEquals(650_000L, fixedExpense,
                    "고정지출: HOUSING(FIXED)·LOAN(FIXED)·INSTALLMENT(FIXED)는 포함, "
                            + "FOOD(VARIABLE)는 제외해야 합니다");
            assertEquals(Map.of("FOOD", 50_000L, "HOUSING", 200_000L, "FINANCE", 450_000L), categories,
                    "카테고리별 소비: LOAN·INSTALLMENT는 TXN_ANALYSIS.category(FINANCE)를 그대로 써야 합니다");
            assertEquals(totalExpense, categories.values().stream().mapToLong(Long::longValue).sum(),
                    "카테고리별 합계는 총소비와 일치해야 합니다");

            // FinancialCommitmentMapper의 "최근 정상 상환 선택"은 TXN_ANALYSIS와 무관하게
            // loan_transaction_type_name 키워드로 독립 판정한다(방어모드 예상 납입액 추정용,
            // 이번 정책 변경의 영향을 받지 않는 별도 로직).
            List<LoanCommitmentCandidateRow> loanCommitments =
                    financialCommitmentMapper.findLoanCommitmentCandidates(
                            USER_ID,
                            LoanDisbursementKeywords.KEYWORDS
                    );
            assertEquals(1, loanCommitments.size(), "LOAN 계좌별 후보는 한 건이어야 합니다");
            LoanCommitmentCandidateRow latestRepayment = loanCommitments.get(0);
            assertEquals(123_000L, latestRepayment.getExpectedAmount().longValueExact(),
                    "공백 포함 지급 거래들을 건너뛰고 직전 정상 상환을 선택해야 합니다");
        }
    }

    private void seedFixtures(DataSource dataSource) throws Exception {
        String day5 = TARGET_MONTH.atDay(5).toString();
        String previousMonthDay5 = TARGET_MONTH.minusMonths(1).atDay(5).toString();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(
                    "DELETE FROM TXN_ANALYSIS WHERE account_transaction_id IN ("
                            + "SELECT account_transaction_id FROM ACCOUNT_TRANSACTION WHERE account_id IN ("
                            + "SELECT account_id FROM ACCOUNT WHERE user_id = " + USER_ID + "))"
            );
            statement.execute(
                    "DELETE FROM ACCOUNT_TRANSACTION WHERE account_id IN ("
                            + "SELECT account_id FROM ACCOUNT WHERE user_id = " + USER_ID + ")"
            );
            statement.execute("DELETE FROM ACCOUNT WHERE user_id = " + USER_ID);
            statement.execute("DELETE FROM CODEF_CONNECTION WHERE user_id = " + USER_ID);

            statement.execute(
                    "INSERT INTO CODEF_CONNECTION (user_id, provider, connected_id) VALUES ("
                            + USER_ID + ", 'NTROPY', 'NTROPY-e2e-143')"
            );
            long connectionId = lastInsertId(statement);

            long ordinaryAccount = insertAccount(statement, connectionId, "DEPOSIT_TRUST", "11", "1101");
            long loanAccount = insertAccount(statement, connectionId, "LOAN", "40", "1102");
            long installmentAccount = insertAccount(statement, connectionId, "DEPOSIT_TRUST", "12", "1103");

            // T1: ORDINARY 변동비 소비
            insertOrdinaryTransaction(statement, ordinaryAccount, day5, 50_000, "e2e-143-t1");
            insertClassification(statement, true, "FOOD", "VARIABLE");

            // T2: ORDINARY 고정비 소비
            insertOrdinaryTransaction(statement, ordinaryAccount, day5, 200_000, "e2e-143-t2");
            insertClassification(statement, true, "HOUSING", "FIXED");

            // T3: ORDINARY 비소비 거래 - 총소비·카테고리·고정지출 어디에도 포함되면 안 된다.
            insertOrdinaryTransaction(statement, ordinaryAccount, day5, 80_000, "e2e-143-t3");
            insertClassification(statement, false, null, null);

            // T4: LOAN, 일간 배치가 이미 정상 상환으로 분류·저장(TRUE/FINANCE/FIXED) - out_amount
            // 300,000 전액이 소비로 반영돼야 한다(원금 포함, #169 최종 정책).
            insertLoanTransaction(statement, loanAccount, day5, 300_000, 250_000, 50_000, "e2e-143-t4");
            insertClassification(statement, true, "FINANCE", "FIXED");

            // T5: LOAN, 일간 배치가 이미 지급 거래로 분류·저장(FALSE/NULL/NULL) - out_amount가
            // 있어도 is_consumption=FALSE이므로 제외돼야 한다.
            insertLoanTransaction(statement, loanAccount, day5, 1_000_000, 1_000_000, null, "e2e-143-t5");
            insertClassification(statement, false, null, null);

            // T6: LOAN, TXN_ANALYSIS 없음(배치 전) - ORDINARY와 동일하게 그 달 집계에서
            // 제외돼야 한다(배치 지연에 따른 일시적 누락은 이제 이 쿼리가 신경 쓰지 않는다).
            insertLoanTransaction(statement, loanAccount, day5, 500_000, 500_000, null, "e2e-143-t6");

            // T7: INSTALLMENT, TXN_ANALYSIS 있음(TRUE/FINANCE/FIXED) - out_amount는 0이므로
            // in_amount(150,000)만 반영돼야 한다.
            insertInstallmentTransaction(statement, installmentAccount, day5, 150_000, "e2e-169-t7");
            insertClassification(statement, true, "FINANCE", "FIXED");

            // T8: INSTALLMENT, TXN_ANALYSIS 없음(배치 전) - 제외돼야 한다.
            insertInstallmentTransaction(statement, installmentAccount, day5, 80_000, "e2e-169-t8");

            // T9: 대상월 밖 LOAN 거래(TXN_ANALYSIS 있음) - 날짜 필터로 제외되어야 한다.
            insertLoanTransaction(statement, loanAccount, previousMonthDay5, 999_999, null, 999_999, "e2e-143-t9");
            insertClassification(statement, true, "FINANCE", "FIXED");

            // 아래는 FinancialCommitmentMapper 전용 픽스처다. TXN_ANALYSIS와 무관하게
            // loan_transaction_type_name 키워드로만 "최근 정상 상환"을 고르므로 분석 행을
            // 만들지 않는다(만들지 않아도 위 월간 소비 집계에는 영향이 없다 - TXN_ANALYSIS가
            // 없으면 자동으로 제외되기 때문).

            // T10: FinancialCommitmentMapper가 선택해야 할 최근 정상 상환(out_amount 123,000).
            insertLoanTransaction(
                    statement, loanAccount, day5, 123_000, 123_000, null,
                    "정상상환", "e2e-169-t10"
            );

            // T11~T13: T10보다 나중에 저장된 공백 포함 지급 거래. FinancialCommitmentMapper가
            // 이 거래들을 건너뛰고 T10을 최근 정상 상환으로 선택해야 한다.
            insertLoanTransaction(
                    statement, loanAccount, day5, 2_100_000, 2_000_000, 100_000,
                    "신 규", "e2e-169-t11"
            );
            insertLoanTransaction(
                    statement, loanAccount, day5, 2_100_000, 2_000_000, 100_000,
                    "실 행", "e2e-169-t12"
            );
            insertLoanTransaction(
                    statement, loanAccount, day5, 2_100_000, 2_000_000, 100_000,
                    "증 액", "e2e-169-t13"
            );
        }
    }

    private void insertInstallmentTransaction(
            Statement statement, long accountId, String date, int inAmount, String fingerprintSeed
    ) throws Exception {
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, tran_date, "
                        + "out_amount, in_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'INSTALLMENT', '" + date + "', "
                        + "0, " + inAmount + ", 1000000)"
        );
    }

    private long insertAccount(
            Statement statement, long connectionId, String accountGroup, String depositTypeCode, String accountNoSeed
    ) throws Exception {
        statement.execute(
                "INSERT INTO ACCOUNT (codef_connection_id, user_id, organization_code, account_group, "
                        + "deposit_type_code, account_no_masked, account_no_hash, balance, currency_code, "
                        + "overdraft_yn, status) VALUES ("
                        + connectionId + ", " + USER_ID + ", '0088', '" + accountGroup + "', '" + depositTypeCode
                        + "', '****" + accountNoSeed + "', SHA2('e2e-143-account-" + accountNoSeed + "', 256), "
                        + "1000000, 'KRW', 0, 'ACTIVE')"
        );
        return lastInsertId(statement);
    }

    private void insertOrdinaryTransaction(
            Statement statement, long accountId, String date, int outAmount, String fingerprintSeed
    ) throws Exception {
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, tran_date, "
                        + "out_amount, in_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'ORDINARY', '" + date + "', "
                        + outAmount + ", 0, 1000000)"
        );
    }

    private void insertLoanTransaction(
            Statement statement, long accountId, String date, int outAmount,
            Integer principalAmount, Integer interestAmount, String fingerprintSeed
    ) throws Exception {
        insertLoanTransaction(
                statement, accountId, date, outAmount, principalAmount, interestAmount, null, fingerprintSeed
        );
    }

    private void insertLoanTransaction(
            Statement statement, long accountId, String date, int outAmount,
            Integer principalAmount, Integer interestAmount, String transactionTypeName, String fingerprintSeed
    ) throws Exception {
        String principalValue = principalAmount == null ? "NULL" : String.valueOf(principalAmount);
        String interestValue = interestAmount == null ? "NULL" : String.valueOf(interestAmount);
        String transactionTypeValue = transactionTypeName == null ? "NULL" : "'" + transactionTypeName + "'";
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, "
                        + "loan_transaction_type_name, tran_date, out_amount, in_amount, "
                        + "loan_principal_amount, loan_interest_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'LOAN', "
                        + transactionTypeValue + ", '" + date + "', " + outAmount + ", 0, "
                        + principalValue + ", " + interestValue + ", 1000000)"
        );
    }

    private void insertClassification(
            Statement statement, boolean isConsumption, String category, String expenseType
    ) throws Exception {
        long transactionId = lastInsertId(statement);
        String categoryValue = category == null ? "NULL" : "'" + category + "'";
        String expenseTypeValue = expenseType == null ? "NULL" : "'" + expenseType + "'";
        statement.execute(
                "INSERT INTO TXN_ANALYSIS (account_transaction_id, is_consumption, category, expense_type) "
                        + "VALUES (" + transactionId + ", " + isConsumption + ", " + categoryValue + ", "
                        + expenseTypeValue + ")"
        );
    }

    private long lastInsertId(Statement statement) throws Exception {
        try (var rs = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = MonthlyExpenseMapper.class)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(System.getenv().getOrDefault(
                    "ACCOUNT_TEST_DB_URL",
                    "jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            ));
            config.setUsername(System.getenv().getOrDefault("ACCOUNT_TEST_DB_USERNAME", "root"));
            config.setPassword(System.getenv().getOrDefault("ACCOUNT_TEST_DB_PASSWORD", "root"));
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
