package com.ntropy.account.client.codef.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.domain.entity.AccountTransaction;

class AccountTransactionResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String HISTORY_LIST_JSON = """
            [
              {
                "resAccountTrDate": "20260115",
                "resAccountTrTime": "093012",
                "resAccountOut": "",
                "resAccountIn": "10,000",
                "resAfterTranBalance": "1,234,567",
                "resAccountDesc1": "홍길동",
                "resAccountDesc2": "이체",
                "resAccountDesc3": "",
                "resAccountDesc4": "강남지점"
              }
            ]
            """;

    @Test
    void flattensTransactionHistoryFromSingleAccountSummaryObject() throws Exception {
        // 계좌 하나만 조회하는 실제 DEMO 응답 형태: data가 배열이 아니라 계좌 요약 객체 하나 (신한 DEMO로 확인).
        JsonNode data = objectMapper.readTree(
                "{\"resAccount\":\"110123456789\",\"resTrHistoryList\":" + HISTORY_LIST_JSON + "}"
        );

        List<AccountTransaction> transactions = AccountTransactionResponseParser.parse(data, 42L);

        assertEquals(1, transactions.size());
        assertPreservesOriginalFields(transactions.get(0));
    }

    @Test
    void flattensTransactionHistoryFromArrayShapedData() throws Exception {
        // 공식 문서가 설명하는 배열 형태도 방어적으로 계속 지원한다.
        JsonNode data = objectMapper.readTree(
                "[{\"resAccount\":\"110123456789\",\"resTrHistoryList\":" + HISTORY_LIST_JSON + "}]"
        );

        List<AccountTransaction> transactions = AccountTransactionResponseParser.parse(data, 42L);

        assertEquals(1, transactions.size());
        assertPreservesOriginalFields(transactions.get(0));
    }

    private static void assertPreservesOriginalFields(AccountTransaction tx) {
        assertEquals(42L, tx.getAccountId());
        assertEquals(LocalDate.of(2026, 1, 15), tx.getTranDate());
        assertEquals(LocalTime.of(9, 30, 12), tx.getTranTime());
        assertEquals(BigDecimal.ZERO, tx.getOutAmount());
        assertEquals(new BigDecimal("10000"), tx.getInAmount());
        assertEquals(new BigDecimal("1234567"), tx.getAfterBalance());
        assertEquals("홍길동", tx.getDesc1());
        assertEquals("이체", tx.getDesc2());
        assertNull(tx.getDesc3());
        assertEquals("강남지점", tx.getDesc4());
        assertNotNull(tx.getFingerprint());
    }

    @Test
    void returnsEmptyListWhenAccountSummaryHasNoHistoryList() throws Exception {
        JsonNode data = objectMapper.readTree("{\"resAccount\":\"110123456789\"}");

        List<AccountTransaction> transactions = AccountTransactionResponseParser.parse(data, 42L);

        assertEquals(0, transactions.size());
    }

    @Test
    void returnsEmptyListWhenDataIsNeitherObjectNorArray() throws Exception {
        JsonNode data = objectMapper.readTree("\"unexpected\"");

        List<AccountTransaction> transactions = AccountTransactionResponseParser.parse(data, 42L);

        assertEquals(0, transactions.size());
    }

    @Test
    void keepsLegacyFingerprintStableWhenOnlyDesc1Changes() throws Exception {
        JsonNode first = objectMapper.readTree(
                "{\"resTrHistoryList\":" + HISTORY_LIST_JSON + "}"
        );
        JsonNode second = objectMapper.readTree(
                "{\"resTrHistoryList\":" + HISTORY_LIST_JSON.replace("홍길동", "김민수") + "}"
        );

        AccountTransaction firstTransaction = AccountTransactionResponseParser.parse(first, 42L).get(0);
        AccountTransaction secondTransaction = AccountTransactionResponseParser.parse(second, 42L).get(0);

        assertEquals(firstTransaction.getFingerprint(), secondTransaction.getFingerprint());
    }
}
