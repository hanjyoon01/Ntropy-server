package com.ntropy.account.client.codef;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.config.CodefProperties;

/**
 * CODEF 업무 API의 공통 전송 계층.
 * 환경별 호스트 선택, 요청 URL 인코딩, 응답 URL 디코딩과 토큰 오류 1회 재시도를 담당한다.
 */
@Component
public class CodefApiClient {

    private static final String INVALID_TOKEN_CODE = "CF-00401";

    private final CodefProperties codefProperties;
    private final CodefAccessTokenProvider tokenProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CodefApiClient(
            CodefProperties codefProperties,
            CodefAccessTokenProvider tokenProvider,
            @Qualifier("codefRestTemplate") RestTemplate restTemplate,
            @Qualifier("codefObjectMapper") ObjectMapper objectMapper
    ) {
        this.codefProperties = codefProperties;
        this.tokenProvider = tokenProvider;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T post(String path, Map<String, Object> requestBody, Class<T> responseType) {
        validatePath(path);
        return post(path, requestBody, responseType, false);
    }

    private <T> T post(String path, Map<String, Object> requestBody, Class<T> responseType, boolean retried) {
        String accessToken = retried
                ? tokenProvider.refreshAccessToken()
                : tokenProvider.getValidAccessToken();

        String decodedResponse;
        try {
            decodedResponse = execute(path, requestBody, accessToken);
        } catch (HttpStatusCodeException e) {
            if (!retried && e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return post(path, requestBody, responseType, true);
            }
            throw new IllegalStateException("CODEF API HTTP 오류: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new IllegalStateException("CODEF API 요청 실패", e);
        }

        try {
            if (!retried && hasInvalidTokenCode(decodedResponse)) {
                return post(path, requestBody, responseType, true);
            }
            return objectMapper.readValue(decodedResponse, responseType);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CODEF API 응답 파싱 실패", e);
        }
    }

    private String execute(String path, Map<String, Object> requestBody, String accessToken) throws Exception {
        String bodyJson = objectMapper.writeValueAsString(requestBody);
        String encodedBody = URLEncoder.encode(bodyJson, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(accessToken);

        ResponseEntity<String> response = restTemplate.exchange(
                codefProperties.getServiceType().getApiBaseUrl() + path,
                HttpMethod.POST,
                new HttpEntity<>(encodedBody, headers),
                String.class
        );

        String rawResponse = response.getBody();
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("CODEF API 응답이 비어 있습니다");
        }
        return URLDecoder.decode(rawResponse, StandardCharsets.UTF_8);
    }

    private boolean hasInvalidTokenCode(String decodedResponse) throws Exception {
        JsonNode root = objectMapper.readTree(decodedResponse);
        return INVALID_TOKEN_CODE.equals(root.path("result").path("code").asText());
    }

    private void validatePath(String path) {
        if (path == null || !path.startsWith("/v1/")) {
            throw new IllegalArgumentException("CODEF API path는 /v1/로 시작해야 합니다");
        }
    }
}
