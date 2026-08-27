package com.ntropy.account.client.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CodefLoanTransactionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void includesLoanExecutionNumberWhenAccountListProvidesIt() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}")
        );
        CodefLoanTransactionClient client = new CodefLoanTransactionClient(apiClient);

        client.getPersonalTransactionList(
                "0011", "connected-id", "302987654321", "execution-1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
        );

        assertEquals("/v1/kr/bank/p/loan/transaction-list", apiClient.path);
        assertEquals("execution-1", apiClient.requestBody.get("accountLoanExecNo"));
        assertFalse(apiClient.requestBody.containsKey("birthDate"));
    }

    @Test
    void omitsLoanExecutionNumberWhenItIsUnavailable() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}")
        );
        CodefLoanTransactionClient client = new CodefLoanTransactionClient(apiClient);

        client.getPersonalTransactionList(
                "0011", "connected-id", "302987654321", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
        );

        assertFalse(apiClient.requestBody.containsKey("accountLoanExecNo"));
    }

    @Test
    void rejectsCodefFailureResponse() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("""
                        {"result":{
                          "code":"CF-12100",
                          "message":"기관 오류",
                          "extraMessage":"계좌 302-123456-7890을 확인하세요",
                          "transactionId":"transaction-abc"
                        },"data":{}}
                        """)
        );
        CodefLoanTransactionClient client = new CodefLoanTransactionClient(apiClient);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.getPersonalTransactionList(
                        "0011", "connected-id", "302987654321", null,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
                )
        );
        assertTrue(exception.getMessage().contains("CF-12100 기관 오류"));
        assertTrue(exception.getMessage().contains("계좌 ******을 확인하세요"));
        assertTrue(exception.getMessage().contains("transactionId=transaction-abc"));
        assertFalse(exception.getMessage().contains("302-123456-7890"));
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
