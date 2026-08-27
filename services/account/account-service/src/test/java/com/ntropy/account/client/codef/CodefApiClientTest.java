package com.ntropy.account.client.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.dto.CodefConnectionCreateResponse;
import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.config.CodefServiceType;

class CodefApiClientTest {

    @Test
    void selectsEnvironmentEncodesBodyAndRetriesInvalidTokenOnce() throws Exception {
        CodefProperties properties = new CodefProperties(
                CodefServiceType.DEMO.name(), "client-id", "client-secret", "public-key", 5000, 10000
        );
        StubTokenProvider tokenProvider = new StubTokenProvider();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("organization", "0004");
        String encodedBody = URLEncoder.encode(
                objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8
        );
        String invalidTokenResponse = URLEncoder.encode(
                "{\"result\":{\"code\":\"CF-00401\",\"message\":\"invalid token\"},\"data\":{}}",
                StandardCharsets.UTF_8
        );
        String successResponse = URLEncoder.encode(
                "{\"result\":{\"code\":\"CF-00000\",\"message\":\"정상\"},"
                        + "\"data\":{\"connectedId\":\"connected-id\",\"successList\":[],\"errorList\":[]}}",
                StandardCharsets.UTF_8
        );

        String url = "https://development.codef.io/v1/account/create";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer cached-token"))
                .andExpect(content().string(encodedBody))
                .andRespond(withSuccess(invalidTokenResponse, MediaType.APPLICATION_JSON));
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer refreshed-token"))
                .andExpect(content().string(encodedBody))
                .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));

        CodefApiClient client = new CodefApiClient(
                properties, tokenProvider, restTemplate, objectMapper
        );

        CodefConnectionCreateResponse response = client.post(
                "/v1/account/create", requestBody, CodefConnectionCreateResponse.class
        );

        assertEquals("connected-id", response.getData().getConnectedId());
        assertEquals(1, tokenProvider.refreshCount);
        server.verify();
    }

    @Test
    void rejectsPathOutsideCodefV1Api() {
        CodefProperties properties = new CodefProperties(
                CodefServiceType.SANDBOX.name(), "client-id", "client-secret", "public-key", 5000, 10000
        );
        CodefApiClient client = new CodefApiClient(
                properties, new StubTokenProvider(), new RestTemplate(), new ObjectMapper()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> client.post("https://example.com", Map.of(), CodefConnectionCreateResponse.class)
        );
    }

    private static class StubTokenProvider implements CodefAccessTokenProvider {
        private int refreshCount;

        @Override
        public String getValidAccessToken() {
            return "cached-token";
        }

        @Override
        public String refreshAccessToken() {
            refreshCount++;
            return "refreshed-token";
        }
    }
}
