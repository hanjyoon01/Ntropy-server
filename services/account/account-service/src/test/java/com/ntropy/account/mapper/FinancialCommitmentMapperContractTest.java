package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FinancialCommitmentMapperContractTest {

    @Test
    void savingQueryScopesByOwnerActiveKrwAndDepositTypeCode() throws IOException {
        String query = selectBody(readMapper(), "findSavingCommitmentCandidates");

        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code = '12'"));
        assertTrue(query.contains("transaction_category = 'INSTALLMENT'"));
        assertTrue(query.contains("installment_row.account_id = account_row.account_id"));
        assertTrue(query.contains("ORDER BY installment_row.tran_date DESC"));
        assertTrue(query.contains("LIMIT 1"));
        assertFalse(query.contains("ROW_NUMBER()"));
    }

    @Test
    void loanQueryScopesByGroupAndExcludesNonRepaymentKeywords() throws IOException {
        String query = selectBody(readMapper(), "findLoanCommitmentCandidates");

        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code = '40'"));
        assertTrue(query.contains("account_row.account_group = 'LOAN'"));
        assertTrue(query.contains("latest_tx.loan_principal_amount AS expectedPrincipalAmount"));
        assertTrue(query.contains("latest_tx.loan_interest_amount AS expectedInterestAmount"));
        assertTrue(query.contains("transaction_category = 'LOAN'"));
        assertTrue(query.contains("loan_row.out_amount > 0"));
        assertTrue(query.contains("<include refid=\"loanDisbursementExclusion\"/>"));
        assertTrue(query.contains("loan_row.account_id = account_row.account_id"));
        assertTrue(query.contains("ORDER BY loan_row.tran_date DESC"));
        assertTrue(query.contains("LIMIT 1"));
        assertFalse(query.contains("ROW_NUMBER()"));
    }

    /**
     * 이슈 #169: LOAN 지급 판정 키워드는 MonthlyExpenseMapper와 동일한 common
     * LoanDisbursementKeywords를 파라미터로 전달받아야 하며, SQL에 리터럴로 남아있지 않아야 한다.
     */
    @Test
    void loanDisbursementExclusionUsesSharedKeywordParameterNotLiterals() throws IOException {
        String fragment = sqlFragmentBody(readMapper(), "loanDisbursementExclusion");

        assertTrue(fragment.contains("<foreach"));
        assertTrue(fragment.contains("collection=\"loanDisbursementKeywords\""));
        assertTrue(fragment.contains("separator=\" AND \""));
        assertTrue(fragment.contains("NOT LIKE CONCAT('%', #{keyword}, '%')"));
        assertFalse(fragment.contains("${keyword}"),
                "키워드 바인딩은 문자열 치환(${keyword})이 아니라 #{keyword}를 써야 합니다");
        assertTrue(fragment.contains("REGEXP_REPLACE"),
                "loan_transaction_type_name의 공백을 정규화한 뒤 비교해야 합니다");
        assertFalse(fragment.contains("신규"),
                "키워드 리터럴은 SQL에 직접 남아있지 않아야 합니다(common LoanDisbursementKeywords 사용)");
        assertFalse(fragment.contains("대출실행"),
                "키워드 리터럴은 SQL에 직접 남아있지 않아야 합니다(common LoanDisbursementKeywords 사용)");
    }

    @Test
    void insuranceQueryScopesByOwnerAndObservationWindowOnOrdinaryOutflows() throws IOException {
        String query = selectBody(readMapper(), "findInsuranceOutflowCandidates");

        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code = '11'"));
        assertTrue(query.contains("transaction_category = 'ORDINARY'"));
        assertTrue(query.contains("transaction_row.out_amount > 0"));
        assertTrue(query.contains(
                "transaction_row.tran_date BETWEEN #{observationStartDate} AND #{observationEndDate}"));
        assertFalse(query.contains("ROW_NUMBER()"));
    }

    private static String selectBody(String mapper, String selectId) {
        int start = mapper.indexOf("<select id=\"" + selectId + "\"");
        int end = mapper.indexOf("</select>", start);
        return mapper.substring(start, end);
    }

    private static String sqlFragmentBody(String mapper, String sqlId) {
        int start = mapper.indexOf("<sql id=\"" + sqlId + "\"");
        int end = mapper.indexOf("</sql>", start);
        return mapper.substring(start, end);
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/FinancialCommitmentMapper.xml";
        try (InputStream input = FinancialCommitmentMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
