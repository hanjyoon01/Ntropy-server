package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MonthlyExpenseMapperContractTest {

    @Test
    void totalExpenseQueryDoesNotFilterByCurrentAccountStatus() throws IOException {
        String query = selectBody(readMapper(), "findTotalExpense");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("analysis_row.is_consumption = TRUE"));
        assertTrue(query.contains("<include refid=\"expenseAmount\"/>"));
    }

    @Test
    void categoryExpensesQueryDoesNotFilterByCurrentAccountStatus() throws IOException {
        String query = selectBody(readMapper(), "findCategoryExpenses");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("analysis_row.is_consumption = TRUE"));
        assertTrue(query.contains("analysis_row.category AS category"));
        assertTrue(query.contains("<include refid=\"expenseAmount\"/>"));
    }

    @Test
    void fixedExpenseQueryDoesNotFilterByCurrentAccountStatusAndScopesToFixedType() throws IOException {
        String query = selectBody(readMapper(), "findFixedExpense");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("analysis_row.is_consumption = TRUE"));
        assertTrue(query.contains("analysis_row.expense_type = 'FIXED'"));
        assertTrue(query.contains("<include refid=\"expenseAmount\"/>"));
    }

    /**
     * 이슈 #143/#169 최종: 세 쿼리 모두 같은 expenseAmount fragment로 금액을 계산해야 계산식이
     * 갈라지지 않는다. LOAN·INSTALLMENT의 소비 판정도 이제 TXN_ANALYSIS를 그대로 신뢰하므로
     * (ORDINARY와 동일 경로), TXN_ANALYSIS가 없는 거래는 그 달 집계에서 빠지는 것이 의도된
     * 동작이라 INNER JOIN을 쓴다.
     */
    @Test
    void allThreeQueriesShareTheSameAmountFragmentAndUseInnerJoinOnAnalysis() throws IOException {
        String mapper = readMapper();
        for (String selectId : new String[] {"findTotalExpense", "findCategoryExpenses", "findFixedExpense"}) {
            String query = selectBody(mapper, selectId);
            assertTrue(query.contains("<include refid=\"expenseAmount\"/>"),
                    selectId + "는 공통 expenseAmount fragment를 재사용해야 합니다");
            assertTrue(query.contains("INNER JOIN TXN_ANALYSIS analysis_row"),
                    selectId + "는 TXN_ANALYSIS가 없는 거래를 배제하도록 INNER JOIN을 써야 합니다");
            assertFalse(query.contains("LEFT JOIN TXN_ANALYSIS"),
                    selectId + "는 더 이상 TXN_ANALYSIS 유무와 무관한 LOAN/INSTALLMENT 특례가 없어야 합니다");
        }
    }

    /**
     * INSTALLMENT는 적립액이 in_amount에 들어오므로(out_amount는 항상 0) 별도 분기가 필요하다.
     * LOAN은 이제 일반 out_amount 규칙과 같아 별도 분기가 없어야 한다(원금 포함, #169 최종 정책).
     */
    @Test
    void expenseAmountOnlyBranchesForInstallmentInAmount() throws IOException {
        String fragment = sqlFragmentBody(readMapper(), "expenseAmount");

        assertTrue(fragment.contains("transaction_category = 'INSTALLMENT'"));
        assertTrue(fragment.contains("in_amount"));
        assertFalse(fragment.contains("'LOAN'"),
                "LOAN은 out_amount를 그대로 쓰는 ELSE 분기와 동일하므로 별도 WHEN 분기가 없어야 합니다");
        assertFalse(fragment.contains("loan_interest_amount"),
                "금액 계산은 더 이상 loan_interest_amount를 쓰지 않아야 합니다");
        assertFalse(fragment.contains("loan_principal_amount"),
                "금액 계산은 loan_principal_amount 컬럼을 직접 쓰지 않아야 합니다");
    }

    /**
     * LOAN 지급(신규·실행·증액) 판정과 카테고리·고정지출 강제는 이제 account-service SQL이 아니라
     * ai-service TransactionPreClassificationService(#148)의 책임이다. 중복 판정 로직이 SQL에
     * 남아있지 않은지 확인한다.
     */
    @Test
    void mapperNoLongerDuplicatesLoanClassificationLogic() throws IOException {
        String mapper = readMapper();

        assertFalse(mapper.contains("loanDisbursementKeywords"),
                "LOAN 지급 판정 키워드 파라미터는 더 이상 이 매퍼에서 쓰이지 않아야 합니다");
        assertFalse(mapper.contains("REGEXP_REPLACE"),
                "지급 거래 정규화/제외 로직은 더 이상 SQL에 없어야 합니다");
        assertFalse(mapper.contains("'FINANCE'"),
                "category='FINANCE' 강제는 더 이상 SQL에 없어야 합니다(TXN_ANALYSIS.category를 그대로 씀)");
    }

    private static String sqlFragmentBody(String mapper, String sqlId) {
        int start = mapper.indexOf("<sql id=\"" + sqlId + "\"");
        int end = mapper.indexOf("</sql>", start);
        return mapper.substring(start, end);
    }

    private static String selectBody(String mapper, String selectId) {
        int start = mapper.indexOf("<select id=\"" + selectId + "\"");
        int end = mapper.indexOf("</select>", start);
        return mapper.substring(start, end);
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/MonthlyExpenseMapper.xml";
        try (InputStream input = MonthlyExpenseMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
