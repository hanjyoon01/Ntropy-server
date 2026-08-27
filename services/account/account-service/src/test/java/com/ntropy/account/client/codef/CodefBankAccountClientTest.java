package com.ntropy.account.client.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CodefBankAccountClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestsPersonalAccountListWithOrganizationAndConnectedId() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree(
                        "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resDepositTrust\":[]}}"
                )
        );
        CodefBankAccountClient client = new CodefBankAccountClient(apiClient);

        JsonNode response = client.getPersonalAccountList("0004", "connected-id");

        assertEquals("CF-00000", response.path("result").path("code").asText());
        assertEquals("/v1/kr/bank/p/account/account-list", apiClient.path);
        assertEquals("0004", apiClient.requestBody.get("organization"));
        assertEquals("connected-id", apiClient.requestBody.get("connectedId"));
    }

    @Test
    void rejectsCodefFailureResponse() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-99999\",\"message\":\"실패\"},\"data\":{}}")
        );
        CodefBankAccountClient client = new CodefBankAccountClient(apiClient);

        assertThrows(
                IllegalStateException.class,
                () -> client.getPersonalAccountList("0004", "connected-id")
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
