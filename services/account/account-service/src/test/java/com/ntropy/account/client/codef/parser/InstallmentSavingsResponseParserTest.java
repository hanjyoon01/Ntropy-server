package com.ntropy.account.client.codef.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.parser.InstallmentSavingsResponseParser.ParsedInstallmentSavings;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;

class InstallmentSavingsResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesDetailAndPaymentHistoryFromSingleObject() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resAccountStatus": "정상",
                  "resFinalRoundNo": "24",
                  "resMonthlyPayment": "100,000",
                  "resValidPeriod": "36",
                  "resType": "일반과세",
                  "resManagementBranch": "영업점",
                  "resRate": "2.56",
                  "resContractAmount": "3,600,000",
                  "resPaymentMethods": "자동이체",
                  "commStartDate": "20250101",
                  "commEndDate": "20260131",
                  "resTrHistoryList": [
                    {
                      "resRoundNo": "13",
                      "resMonth": "01",
                      "resAccountTrDate": "20260105",
                      "resAccountIn": "100,000",
                      "resAccountDesc2": "월불입금",
                      "resAfterTranBalance": "1,300,000"
                    }
                  ]
                }
                """);

        List<ParsedInstallmentSavings> parsed = InstallmentSavingsResponseParser.parse(data, 42L);

        assertEquals(1, parsed.size());
        ParsedInstallmentSavings result = parsed.get(0);
        assertEquals(new BigDecimal("2.56"), result.detail().getInterestRate());
        assertNull(result.detail().getMaturityDate(), "fixture에 resAccountEndDate가 없으면 null이어야 한다");
        assertNull(result.detail().getNextPaymentDate(), "CODEF 적금 거래내역에는 다음 납입일 필드가 없어 항상 null이어야 한다");
        assertEquals(1, result.transactions().size());
        AccountTransaction tx = result.transactions().get(0);
        assertEquals(42L, tx.getAccountId());
        assertEquals(AccountTransactionCategory.INSTALLMENT, tx.getTransactionCategory());
        assertEquals(LocalDate.of(2026, 1, 5), tx.getTranDate());
        assertEquals(new BigDecimal("100000"), tx.getInAmount());
        assertEquals(new BigDecimal("1300000"), tx.getAfterBalance());
        assertEquals("월불입금", tx.getDesc2());
        assertNotNull(tx.getFingerprint());
    }

    @Test
    void parsesMaturityDateWhenProvided() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resRate": "2.56",
                  "resAccountEndDate": "20270101",
                  "resTrHistoryList": []
                }
                """);

        Account detail = InstallmentSavingsResponseParser.parse(data, 42L).get(0).detail();

        assertEquals(LocalDate.of(2027, 1, 1), detail.getMaturityDate());
        assertNull(detail.getNextPaymentDate());
    }

    @Test
    void allowsOptionalHistoryFieldsToBeEmpty() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "resTrHistoryList": [
                    {"resAccountIn": "", "resAfterTranBalance": ""}
                  ]
                }
                """);

        AccountTransaction tx = InstallmentSavingsResponseParser.parse(data, 42L)
                .get(0).transactions().get(0);

        assertEquals(BigDecimal.ZERO, tx.getInAmount());
        assertNull(tx.getTranDate());
        assertNull(tx.getAfterBalance());
    }
}
