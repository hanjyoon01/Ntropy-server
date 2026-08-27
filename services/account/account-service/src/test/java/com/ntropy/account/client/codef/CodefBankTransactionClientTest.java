package com.ntropy.account.client.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CodefBankTransactionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestsTransactionListWithFixedOrderByAndInquiryType() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}")
        );
        CodefBankTransactionClient client = new CodefBankTransactionClient(apiClient);

        JsonNode response = client.getPersonalTransactionList(
                "0088", "connected-id", "110123456789",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
        );

        assertEquals("CF-00000", response.path("result").path("code").asText());
        assertEquals("/v1/kr/bank/p/account/transaction-list", apiClient.path);
        assertEquals("0088", apiClient.requestBody.get("organization"));
        assertEquals("connected-id", apiClient.requestBody.get("connectedId"));
        assertEquals("110123456789", apiClient.requestBody.get("account"));
        assertEquals("20260101", apiClient.requestBody.get("startDate"));
        assertEquals("20260131", apiClient.requestBody.get("endDate"));
        assertEquals("0", apiClient.requestBody.get("orderBy"));
        assertEquals("1", apiClient.requestBody.get("inquiryType"));
        assertFalse(apiClient.requestBody.containsKey("birthDate"));
    }

    @Test
    void includesBirthDateWhenProvided() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}")
        );
        CodefBankTransactionClient client = new CodefBankTransactionClient(apiClient);

        client.getPersonalTransactionList(
                "0004", "connected-id", "110123456789",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "19900101"
        );

        assertTrue(apiClient.requestBody.containsKey("birthDate"));
        assertEquals("19900101", apiClient.requestBody.get("birthDate"));
    }

    @Test
    void rejectsCodefFailureResponse() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-99999\",\"message\":\"실패\"},\"data\":[]}")
        );
        CodefBankTransactionClient client = new CodefBankTransactionClient(apiClient);

        assertThrows(
                IllegalStateException.class,
                () -> client.getPersonalTransactionList(
                        "0088", "connected-id", "110123456789",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
                )
        );
    }

    @Test
    void acceptsDataAsSingleAccountSummaryObject() throws Exception {
        // 공식 문서는 data를 배열이라고 설명하지만, 계좌 하나만 조회하는 실제 DEMO 응답은
        // 배열이 아니라 계좌 요약 객체 하나를 그대로 반환한다 (신한 DEMO로 확인).
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resAccount\":\"110123456789\"}}")
        );
        CodefBankTransactionClient client = new CodefBankTransactionClient(apiClient);

        JsonNode response = client.getPersonalTransactionList(
                "0088", "connected-id", "110123456789",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
        );

        assertEquals("110123456789", response.path("data").path("resAccount").asText());
    }

    @Test
    void rejectsResponseWhenDataIsNeitherObjectNorArray() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":\"unexpected\"}")
        );
        CodefBankTransactionClient client = new CodefBankTransactionClient(apiClient);

        assertThrows(
                IllegalStateException.class,
                () -> client.getPersonalTransactionList(
                        "0088", "connected-id", "110123456789",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
                )
        );
    }

    private static class StubCodefApiClient extends CodefApiClient {

        private final JsonNode response;
        private String path;
        private Map<String, Object> requestBody;

        StubCodefApiClient(JsonNode response) {
            super(null, null, null, null);
            this.response = response;
        }

        @Override
        public <T> T post(String path, Map<String, Object> requestBody, Class<T> responseType) {
            this.path = path;
            this.requestBody = requestBody;
            return responseType.cast(response);
        }
    }
}
