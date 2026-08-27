package com.ntropy.account.client.codef.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.parser.LoanTransactionResponseParser.ParsedLoan;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.entity.AccountTransaction;

class LoanTransactionResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesLoanDetailAndPrincipalInterestHistory() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resAccountStartDate": "20200101",
                  "resAccountEndDate": "20300101",
                  "resDatePayment": "20260205",
                  "resPrincipal": "120,000,000",
                  "resLoanKind": "주택담보대출",
                  "resLoanBalance": "95,000,000",
                  "resState": "정상",
                  "resRate": "3.45",
                  "commStartDate": "20250101",
                  "commEndDate": "20260131",
                  "resTrHistoryList": [
                    {
                      "commStartDate": "20251206",
                      "commEndDate": "20260105",
                      "resAccountTrDate": "20260105",
                      "resTransTypeNm": "원리금상환",
                      "resTranAmount": "1,200,000",
                      "resPrincipal": "900,000",
                      "resInterest": "300,000",
                      "resInterestRate": "3.45",
                      "resType": "정상이자",
                      "resLoanBalance": "95,000,000"
                    }
                  ]
                }
                """);

        List<ParsedLoan> parsed = LoanTransactionResponseParser.parse(data, 77L);

        assertEquals(1, parsed.size());
        ParsedLoan result = parsed.get(0);
        assertEquals(new BigDecimal("95000000"), result.detail().getBalance());
        assertEquals(LocalDate.of(2026, 2, 5), result.detail().getNextPaymentDate());
        // 계좌 상세 상위 resPrincipal(약정원금)·resRate(약정이율)·resAccountEndDate(만기일)
        assertEquals(new BigDecimal("120000000"), result.detail().getLoanContractPrincipal());
        assertEquals(new BigDecimal("3.45"), result.detail().getInterestRate());
        assertEquals(LocalDate.of(2030, 1, 1), result.detail().getMaturityDate());

        assertEquals(1, result.transactions().size());
        AccountTransaction tx = result.transactions().get(0);
        assertEquals(AccountTransactionCategory.LOAN, tx.getTransactionCategory());
        assertEquals(LocalDate.of(2026, 1, 5), tx.getTranDate());
        assertEquals(new BigDecimal("1200000"), tx.getOutAmount());
        assertEquals(new BigDecimal("95000000"), tx.getAfterBalance());
        // 반복부 resTransTypeNm·resPrincipal(거래 원금)·resInterest(거래 이자)
        assertEquals("원리금상환", tx.getLoanTransactionTypeName());
        assertEquals(new BigDecimal("900000"), tx.getLoanPrincipalAmount());
        assertEquals(new BigDecimal("300000"), tx.getLoanInterestAmount());
        assertNotNull(tx.getFingerprint());
    }

    @Test
    void fallsBackToPrincipalPlusInterestWhenTranAmountMissing() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resPrincipal": "120,000,000",
                  "resRate": "3.45",
                  "resLoanBalance": "95,000,000",
                  "resTrHistoryList": [
                    {
                      "resAccountTrDate": "20260105",
                      "resTransTypeNm": "원리금상환",
                      "resPrincipal": "900,000",
                      "resInterest": "300,000",
                      "resLoanBalance": "95,000,000"
                    }
                  ]
                }
                """);

        AccountTransaction tx = LoanTransactionResponseParser.parse(data, 77L).get(0).transactions().get(0);

        assertEquals(new BigDecimal("1200000"), tx.getOutAmount());
    }

    @Test
    void doesNotMixRepeatingInterestRateIntoAccountRate() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resRate": "3.45",
                  "resTrHistoryList": [
                    {
                      "resAccountTrDate": "20260105",
                      "resTranAmount": "1,200,000",
                      "resInterestRate": "9.99",
                      "resLoanBalance": "95,000,000"
                    }
                  ]
                }
                """);

        ParsedLoan result = LoanTransactionResponseParser.parse(data, 77L).get(0);

        // 반복부 resInterestRate(거래 시점 이율)는 계좌 약정이율에 반영되지 않는다.
        assertEquals(new BigDecimal("3.45"), result.detail().getInterestRate());
    }

    @Test
    void leavesOptionalDetailFieldsNullWhenMissing() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resTrHistoryList": []
                }
                """);

        ParsedLoan result = LoanTransactionResponseParser.parse(data, 77L).get(0);

        assertEquals(null, result.detail().getLoanContractPrincipal());
        assertEquals(null, result.detail().getInterestRate());
        assertEquals(null, result.detail().getMaturityDate());
    }
}
