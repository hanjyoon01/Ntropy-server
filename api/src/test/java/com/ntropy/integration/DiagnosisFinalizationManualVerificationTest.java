package com.ntropy.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.Collections;
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

import com.ntropy.account.api.client.FinancialPositionQueryClient;
import com.ntropy.account.api.client.MonthlyExpenseQueryClient;
import com.ntropy.account.client.LocalFinancialPositionQueryClient;
import com.ntropy.account.mapper.FinancialPositionMapper;
import com.ntropy.account.mapper.MonthlyExpenseMapper;
import com.ntropy.account.service.FinancialPositionService;
import com.ntropy.account.service.MonthlyExpenseService;
import com.ntropy.defense.api.dto.command.DefenseModeEnterCommand;
import com.ntropy.defense.adapter.diagnosis.DiagnosisSnapshotAdapter;
import com.ntropy.defense.domain.DefenseCalculationStatus;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseModeStatus;
import com.ntropy.defense.mapper.DefenseModeMapper;
import com.ntropy.defense.service.DefenseModeService;
import com.ntropy.diagnosis.adapter.account.FinancialPositionAdapter;
import com.ntropy.diagnosis.adapter.account.MonthlyExpenseAdapter;
import com.ntropy.diagnosis.api.dto.DiagnosisAnalysisSummary;
import com.ntropy.diagnosis.client.LocalDiagnosisAnalysisQueryClient;
import com.ntropy.diagnosis.client.LocalDiagnosisCommandClient;
import com.ntropy.diagnosis.client.LocalDiagnosisQueryClient;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.dto.DiagnosisCalculationInput;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.port.work.IncomeAnalysisPort;
import com.ntropy.diagnosis.port.work.MonthlyIncomeAnalysis;
import com.ntropy.diagnosis.service.CategoryExpenseCalculator;
import com.ntropy.diagnosis.service.DiagnosisResultService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #138(재계산·월별 확정)을 실제 MySQL 기준으로 검증하는 수동 테스트입니다.
 *
 * <p>단위 테스트는 in-memory Mapper로 애플리케이션 로직만 검증하지만, 이 테스트는
 * (1) 시점 기반 잔액 재구성의 상관 서브쿼리·DATE_ADD 활성 판정, (2) 월별 소비 집계에서
 * 계좌 상태 필터 제거, (3) 확정된 행을 보호하는 조건부 upsert SET절, (4) LOAN 집계 규칙(TXN_ANALYSIS를
 * ORDINARY와 동일하게 신뢰하되 금액은 out_amount 전액, #169 최종 정책) - 네 가지 모두 실제 MySQL에서
 * 의도한 대로 동작하는지 검증합니다. RUN_DIAGNOSIS_FINALIZATION_TEST=true일
 * 때만 실행합니다. account-service/diagnosis-service/defense-service를 모두 참조해야 해서
 * 세 모듈을 전부 의존성으로 갖는 api 모듈에 둡니다(DiagnosisDefenseWiringTest와 동일한 이유).</p>
 *
 * <p>대상 연월은 실행 시점 기준 지난달로 고정합니다(Clock 주입 없이 실제 시스템 시계 사용) -
 * 재계산 시점에 항상 "이미 끝난 과거월"이 되도록 해서 확정 경로를 안정적으로 태웁니다.</p>
 */
class DiagnosisFinalizationManualVerificationTest {

    private static final Long USER_ID = 9_999_999_777L;

    @Test
    void recalculatePastMonth_finalizesWithRealDataAndFeedsDefenseMode() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_DIAGNOSIS_FINALIZATION_TEST")),
                "실제 MySQL이 필요한 이슈 #138 종단 검증용 테스트"
        );

        YearMonth targetMonth = YearMonth.now().minusMonths(1);
        String targetMonthEnd = targetMonth.atEndOfMonth().toString();
        String afterTargetMonth = targetMonth.plusMonths(1).atDay(5).toString();

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            seedFixtures(dataSource, targetMonth, targetMonthEnd, afterTargetMonth);

            StubIncomeAnalysisQueryClient income = new StubIncomeAnalysisQueryClient();
            income.totalIncome = 3_000_000L;
            income.unmatchedIncome = 0L;

            DiagnosisResultService diagnosisResultService = context.getBean(DiagnosisResultService.class);
            MonthlyExpenseQueryClient monthlyExpenseQueryClient = context.getBean(MonthlyExpenseQueryClient.class);
            FinancialPositionQueryClient financialPositionQueryClient =
                    context.getBean(FinancialPositionQueryClient.class);

            LocalDiagnosisCommandClient commandClient = new LocalDiagnosisCommandClient(
                    diagnosisResultService, income,
                    new MonthlyExpenseAdapter(monthlyExpenseQueryClient),
                    new FinancialPositionAdapter(financialPositionQueryClient)
            );

            commandClient.recalculate(USER_ID, targetMonth);

            DiagnosisResultMapper diagnosisResultMapper = context.getBean(DiagnosisResultMapper.class);
            DiagnosisResult finalized =
                    diagnosisResultMapper.findByUserIdAndYearMonth(USER_ID, targetMonth.toString());

            assertNotNull(finalized, "재계산 결과가 저장되지 않았습니다");
            assertNotNull(finalized.getFinalizedAt(), "과거월인데 확정되지 않았습니다");
            // 월말 이후 거래(after_balance=9999999)가 asOf 재구성에 섞이지 않았는지 확인한다.
            // LOAN 계좌(C)는 deposit_type_code=40이라 liquidAssets/safeAssets에는 포함되지 않는다.
            assertEquals(1_950_000L, finalized.getLiquidAssets(), "시점 기반 자산 재구성 결과가 다릅니다");
            assertEquals(0L, finalized.getSafeAssets());
            // 비활성화된 계좌 B(100,000)가 소비 집계에서 빠지지 않았는지 확인한다.
            // 650,000(기존 ORDINARY 소비) + 320,000(LOAN 원금+이자 전액, #169 후속 정책 변경:
            // c-1 200,000 + c-2 120,000) = 970,000.
            assertEquals(970_000L, finalized.getTotalExpense(), "비활성 계좌의 과거 소비 또는 LOAN 상환액 반영이 누락됐습니다");
            // 400,000(기존 FIXED) + 320,000(LOAN 상환액은 예정된 반복 상환이라 고정지출로도 집계) = 720,000.
            assertEquals(720_000L, finalized.getFixedExpense());
            assertEquals(2_030_000L, finalized.getNetCashFlow());

            // 확정된 행을 다른 값으로 다시 upsert 시도한다. 애플리케이션 사전 체크를 우회해
            // SQL의 조건부 SET절 자체가 실제 MySQL에서 보호하는지 직접 검증한다.
            DiagnosisCalculationInput differentInput = new DiagnosisCalculationInput(
                    USER_ID, targetMonth.toString(), 1L, 0L, 1L, 0L, 1L, 1L, 0L
            );
            diagnosisResultService.calculateAndUpsert(differentInput, true);

            DiagnosisResult afterOverwriteAttempt =
                    diagnosisResultMapper.findByUserIdAndYearMonth(USER_ID, targetMonth.toString());
            assertEquals(finalized.getTotalExpense(), afterOverwriteAttempt.getTotalExpense(),
                    "확정된 행이 SQL 레벨에서 보호되지 않고 덮어써졌습니다");
            assertEquals(finalized.getLiquidAssets(), afterOverwriteAttempt.getLiquidAssets());
            assertEquals(finalized.getFinalizedAt(), afterOverwriteAttempt.getFinalizedAt());

            // 월별 분석 조회도 스칼라·카테고리 breakdown을 정상 조합하는지 확인한다.
            LocalDiagnosisAnalysisQueryClient analysisQueryClient =
                    context.getBean(LocalDiagnosisAnalysisQueryClient.class);
            DiagnosisAnalysisSummary analysis = analysisQueryClient.getMonthlyAnalysis(USER_ID, targetMonth);
            assertTrue(analysis.isFinalized());
            assertEquals(970_000L, analysis.getTotalExpense());
            assertFalse(analysis.getCategoryExpenses().isEmpty());
            // #169 후속 정책 변경: LOAN 상환액(200,000 + 120,000, 원금 포함)이 모두
            // category='FINANCE'로 합산된다.
            long financeCategoryAmount = analysis.getCategoryExpenses().stream()
                    .filter(category -> "FINANCE".equals(category.getCategory()))
                    .mapToLong(category -> category.getAmount() == null ? 0L : category.getAmount())
                    .findFirst()
                    .orElse(-1L);
            assertEquals(320_000L, financeCategoryAmount, "LOAN 상환액 카테고리(FINANCE) 합계가 다릅니다");
            long categoryExpenseSum = analysis.getCategoryExpenses().stream()
                    .mapToLong(category -> category.getAmount() == null ? 0L : category.getAmount())
                    .sum();
            assertEquals(analysis.getTotalExpense(), categoryExpenseSum, "카테고리별 합계가 총소비와 일치해야 합니다");

            // 방어모드가 실제 DiagnosisQueryClient를 통해 이 값을 받아 D-Day까지 정상 계산하는지 확인한다.
            LocalDiagnosisQueryClient queryClient = context.getBean(LocalDiagnosisQueryClient.class);
            DefenseModeService defenseModeService = new DefenseModeService(
                    new InMemoryDefenseModeMapper(),
                    new DiagnosisSnapshotAdapter(queryClient),
                    (userId, from, to) -> Collections.emptyList(),
                    (userId, from, to) -> Collections.emptyList()
            );

            java.time.LocalDate today = java.time.LocalDate.now();
            DefenseMode entered = defenseModeService.enter(new DefenseModeEnterCommand(
                    USER_ID, "OTHER", today, today.plusDays(10)
            ));

            assertEquals(DefenseCalculationStatus.CALCULATED, entered.getCalculationStatus());
            assertEquals(1_950_000L, entered.getReserveAmountSnapshot());
            assertEquals(0L, entered.getSafeAssetAmountSnapshot());
            assertEquals(1_950_000L, entered.getAvailableAssetsSnapshot());
            // 완료 월은 실제 일수(28~31일)로 30일 환산하므로 실행 월과 무관하게
            // 동일한 규칙으로 dailyExpense와 D-Day 기대값을 계산한다.
            BigDecimal normalizedMonthlyExpense = BigDecimal.valueOf(finalized.getTotalExpense())
                    .multiply(BigDecimal.valueOf(30))
                    .divide(BigDecimal.valueOf(targetMonth.lengthOfMonth()), 10, RoundingMode.HALF_UP);
            long expectedAverageMonthlyExpense = normalizedMonthlyExpense
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            long expectedDailyExpense = (long) Math.ceil(expectedAverageMonthlyExpense / 30.0);
            int expectedDDay = (int) (entered.getAvailableAssetsSnapshot() / expectedDailyExpense);
            assertEquals(expectedDailyExpense, entered.getDailyExpense());
            assertEquals(expectedDDay, entered.getDDay());

            System.out.println("DIAGNOSIS_FINALIZATION_TEST_TARGET_MONTH=" + targetMonth);
            System.out.println("DIAGNOSIS_FINALIZATION_TEST_D_DAY=" + entered.getDDay());
        }
    }

    private void seedFixtures(
            DataSource dataSource, YearMonth targetMonth, String targetMonthEnd, String afterTargetMonth
    ) throws Exception {
        String day5 = targetMonth.atDay(5).toString();
        String day8 = targetMonth.atDay(8).toString();
        String day10 = targetMonth.atDay(10).toString();
        String day15 = targetMonth.atDay(15).toString();
        String day20 = targetMonth.atDay(20).toString();
        String deactivatedAt = targetMonth.plusMonths(1).atDay(1) + " 10:00:00";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 이전 실행 잔여 데이터 정리(같은 테스트 사용자 범위만).
            statement.execute("DELETE FROM DIAGNOSIS_RESULT WHERE user_id = " + USER_ID);
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
                            + USER_ID + ", 'NTROPY', 'NTROPY-e2e-138')"
            );
            long connectionId = lastInsertId(statement);

            // 계좌 A: 계속 ACTIVE. 대상월 마지막 거래가 asOf(월말) 잔액이 되고, 다음달 거래는 제외돼야 한다.
            statement.execute(
                    "INSERT INTO ACCOUNT (codef_connection_id, user_id, organization_code, account_group, "
                            + "deposit_type_code, account_no_masked, account_no_hash, balance, currency_code, "
                            + "overdraft_yn, status) VALUES ("
                            + connectionId + ", " + USER_ID + ", '0088', 'DEPOSIT_TRUST', '11', "
                            + "'****1001', SHA2('e2e-138-account-a', 256), 1450000, 'KRW', 0, 'ACTIVE')"
            );
            long accountA = lastInsertId(statement);

            // 계좌 B: 다음달 1일 10시에 이미 비활성화됨 - 대상월 말일 시점에는 활성이었어야 asOf 조회에
            // 포함된다. 소비 집계에서도 "지금은 비활성"인 이 계좌의 대상월 거래가 여전히 잡혀야 한다
            // (ACTIVE 필터 제거 검증).
            statement.execute(
                    "INSERT INTO ACCOUNT (codef_connection_id, user_id, organization_code, account_group, "
                            + "deposit_type_code, account_no_masked, account_no_hash, balance, currency_code, "
                            + "overdraft_yn, status, deactivated_at) VALUES ("
                            + connectionId + ", " + USER_ID + ", '0088', 'DEPOSIT_TRUST', '11', "
                            + "'****1002', SHA2('e2e-138-account-b', 256), 500000, 'KRW', 0, 'INACTIVE', '"
                            + deactivatedAt + "')"
            );
            long accountB = lastInsertId(statement);

            insertTransaction(statement, accountA, day5, 300_000, 0, 1_500_000, "e2e-138-a-1");
            insertClassification(statement, true, "HOUSING", "FIXED");

            insertTransaction(statement, accountA, day10, 200_000, 0, 1_300_000, "e2e-138-a-2");
            insertClassification(statement, true, "FOOD", "VARIABLE");

            // 입금(비소비) 거래 - 총소비에 포함되면 안 된다.
            insertTransaction(statement, accountA, day20, 0, 3_000_000, 4_300_000, "e2e-138-a-3");
            insertClassification(statement, false, null, null);

            insertTransaction(statement, accountA, targetMonthEnd, 50_000, 0, 1_450_000, "e2e-138-a-4");
            insertClassification(statement, true, "SHOPPING", "VARIABLE");

            // 대상월 범위 밖 거래 - asOf(월말) 재구성과 월별 소비 집계 양쪽에서 제외돼야 한다.
            insertTransaction(statement, accountA, afterTargetMonth, 10_000, 0, 9_999_999, "e2e-138-a-5");
            insertClassification(statement, true, "ETC", "VARIABLE");

            insertTransaction(statement, accountB, day15, 100_000, 0, 500_000, "e2e-138-b-1");
            insertClassification(statement, true, "INSURANCE", "FIXED");

            // 계좌 C: LOAN 전용 계좌. LOAN도 이제 ORDINARY와 동일하게 TXN_ANALYSIS를 그대로
            // 신뢰하므로(ai-service TransactionPreClassificationService가 이미 분류·저장했다고
            // 가정), 두 거래 모두 TRUE/FINANCE/FIXED로 분류해둔다. 금액은 out_amount 전액이
            // 소비로 반영된다(원금 포함, #169 최종 정책).
            // deposit_type_code=40이라 liquidAssets/safeAssets에는 섞이지 않는다(FinancialPositionService 참고).
            statement.execute(
                    "INSERT INTO ACCOUNT (codef_connection_id, user_id, organization_code, account_group, "
                            + "deposit_type_code, account_no_masked, account_no_hash, balance, currency_code, "
                            + "overdraft_yn, status) VALUES ("
                            + connectionId + ", " + USER_ID + ", '0088', 'LOAN', '40', "
                            + "'****1003', SHA2('e2e-143-account-c', 256), 5000000, 'KRW', 0, 'ACTIVE')"
            );
            long accountC = lastInsertId(statement);

            // out_amount(200,000)가 원금+이자 합(180,000)과 다른 실제 CODEF 케이스를 재현한다 - CODEF가
            // resTranAmount를 총상환액으로 별도 제공하고 원금·이자와 반드시 일치하지 않을 수 있다는 전제(참고
            // LoanTransactionResponseParser.repaymentAmount). #169 최종 정책으로 원금+이자 합과의
            // 불일치 여부와 무관하게 out_amount(200,000)를 그대로 소비로 반영한다.
            insertLoanTransaction(statement, accountC, day5, 200_000, 150_000, 30_000, 4_850_000, "e2e-143-c-1");
            insertClassification(statement, true, "FINANCE", "FIXED");

            // 원금만 있고 이자가 null인 거래 - 원금도 이제 소비이므로 out_amount(120,000) 전액을 반영한다.
            insertLoanTransaction(statement, accountC, day8, 120_000, 120_000, null, 4_730_000, "e2e-143-c-2");
            insertClassification(statement, true, "FINANCE", "FIXED");
        }
    }

    private void insertLoanTransaction(
            Statement statement, long accountId, String date, int outAmount,
            Integer principalAmount, Integer interestAmount, int afterBalance, String fingerprintSeed
    ) throws Exception {
        String principalValue = principalAmount == null ? "NULL" : String.valueOf(principalAmount);
        String interestValue = interestAmount == null ? "NULL" : String.valueOf(interestAmount);
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, "
                        + "loan_transaction_type_name, tran_date, out_amount, in_amount, "
                        + "loan_principal_amount, loan_interest_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'LOAN', '원리금상환', '"
                        + date + "', "
                        + outAmount + ", 0, " + principalValue + ", " + interestValue + ", " + afterBalance + ")"
        );
    }

    private void insertTransaction(
            Statement statement, long accountId, String date, int outAmount, int inAmount, int afterBalance,
            String fingerprintSeed
    ) throws Exception {
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, tran_date, "
                        + "out_amount, in_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'ORDINARY', '" + date + "', "
                        + outAmount + ", " + inAmount + ", " + afterBalance + ")"
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

    private static class StubIncomeAnalysisQueryClient implements IncomeAnalysisPort {
        Long totalIncome;
        Long unmatchedIncome;

        @Override
        public MonthlyIncomeAnalysis getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth) {
            return new MonthlyIncomeAnalysis(totalIncome, unmatchedIncome);
        }
    }

    private static class InMemoryDefenseModeMapper implements DefenseModeMapper {
        private final java.util.Map<Long, DefenseMode> data = new java.util.HashMap<>();
        private long sequence;

        @Override
        public DefenseMode findById(Long defenseId) {
            return data.get(defenseId);
        }

        @Override
        public DefenseMode findActiveByUserId(Long userId) {
            return data.values().stream()
                    .filter(mode -> userId.equals(mode.getUserId()))
                    .filter(mode -> mode.getStatus() == DefenseModeStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public DefenseMode findCurrentByUserId(Long userId) {
            return data.values().stream()
                    .filter(mode -> userId.equals(mode.getUserId()))
                    .filter(mode -> mode.getStatus() == DefenseModeStatus.ACTIVE
                            || mode.getStatus() == DefenseModeStatus.SCHEDULED)
                    .findFirst().orElse(null);
        }

        @Override
        public java.util.List<DefenseMode> findScheduledToActivate(java.time.LocalDate today) {
            return data.values().stream()
                    .filter(mode -> mode.getStatus() == DefenseModeStatus.SCHEDULED)
                    .filter(mode -> !mode.getUnavailableStartDate().isAfter(today))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<DefenseMode> findCalendarPeriods(Long userId, java.time.LocalDate from, java.time.LocalDate to) {
            return List.of();
        }

        @Override
        public int insert(DefenseMode defenseMode) {
            defenseMode.setDefenseId(++sequence);
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }

        @Override
        public int activate(DefenseMode defenseMode) {
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }

        @Override
        public int release(DefenseMode defenseMode) {
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {
            FinancialPositionMapper.class, MonthlyExpenseMapper.class, DiagnosisResultMapper.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(System.getenv().getOrDefault(
                    "DIAGNOSIS_TEST_DB_URL",
                    "jdbc:mysql://localhost:3307/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            ));
            config.setUsername(System.getenv().getOrDefault("DIAGNOSIS_TEST_DB_USERNAME", "root"));
            config.setPassword(System.getenv().getOrDefault("DIAGNOSIS_TEST_DB_PASSWORD", "root"));
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
        FinancialPositionService financialPositionService(FinancialPositionMapper mapper) {
            return new FinancialPositionService(mapper);
        }

        @Bean
        FinancialPositionQueryClient financialPositionQueryClient(FinancialPositionService service) {
            return new LocalFinancialPositionQueryClient(service);
        }

        @Bean
        MonthlyExpenseService monthlyExpenseService(MonthlyExpenseMapper mapper) {
            return new MonthlyExpenseService(mapper);
        }

        @Bean
        MonthlyExpenseQueryClient monthlyExpenseQueryClient(MonthlyExpenseService service) {
            return service::findMonthlyExpense;
        }

        @Bean
        DiagnosisResultService diagnosisResultService(DiagnosisResultMapper mapper) {
            return new DiagnosisResultService(mapper);
        }

        @Bean
        LocalDiagnosisAnalysisQueryClient localDiagnosisAnalysisQueryClient(
                DiagnosisResultService diagnosisResultService,
                MonthlyExpenseQueryClient monthlyExpenseQueryClient
        ) {
            return new LocalDiagnosisAnalysisQueryClient(
                    diagnosisResultService, monthlyExpenseQueryClient, new CategoryExpenseCalculator()
            );
        }

        @Bean
        LocalDiagnosisQueryClient localDiagnosisQueryClient(DiagnosisResultService diagnosisResultService) {
            return new LocalDiagnosisQueryClient(diagnosisResultService);
        }
    }
}
