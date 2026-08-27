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

class CodefInstallmentSavingsClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestsInstallmentSavingsTransactionsForNhWithoutBirthDate() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}")
        );
        CodefInstallmentSavingsClient client = new CodefInstallmentSavingsClient(apiClient);

        client.getPersonalTransactionList(
                "0011", "connected-id", "302123456789",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
        );

        assertEquals("/v1/kr/bank/p/installment-savings/transaction-list", apiClient.path);
        assertEquals("0011", apiClient.requestBody.get("organization"));
        assertEquals("connected-id", apiClient.requestBody.get("connectedId"));
        assertEquals("302123456789", apiClient.requestBody.get("account"));
        assertEquals("20260101", apiClient.requestBody.get("startDate"));
        assertEquals("20260131", apiClient.requestBody.get("endDate"));
        assertEquals("0", apiClient.requestBody.get("orderBy"));
        assertEquals("1", apiClient.requestBody.get("inquiryType"));
        assertFalse(apiClient.requestBody.containsKey("birthDate"));
    }

    @Test
    void rejectsCodefFailureResponse() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("""
                        {"result":{
                          "code":"CF-12200",
                          "message":"통신 실패",
                          "extraMessage":"잠시 후 다시 시도하세요"
                        },"data":{}}
                        """)
        );
        CodefInstallmentSavingsClient client = new CodefInstallmentSavingsClient(apiClient);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.getPersonalTransactionList(
                        "0011", "connected-id", "302123456789",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
                )
        );
        assertTrue(exception.getMessage().contains("CF-12200 통신 실패 - 잠시 후 다시 시도하세요"));
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
