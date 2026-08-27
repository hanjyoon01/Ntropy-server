package com.ntropy.account.client.codef;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.dto.CodefTokenResponse;
import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.domain.entity.CodefToken;
import com.ntropy.account.repository.CodefTokenStore;

/**
 * CODEF OAuth2 accessToken을 발급/캐싱해서 돌려준다.
 * {@link CodefTokenStore}에 유효한 토큰이 있으면 재사용하고(TTL + lazy refresh),
 * 없거나 만료됐으면 CODEF에 새로 발급받아 저장한다.
 */
@Component
public class CodefOAuthClient implements CodefAccessTokenProvider {

    private static final String TOKEN_URL = "https://oauth.codef.io/oauth/token";
    private static final Duration TOKEN_REFRESH_SKEW = Duration.ofHours(1);

    private final CodefProperties codefProperties;
    private final CodefTokenStore codefTokenStore;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CodefOAuthClient(
            CodefProperties codefProperties,
            CodefTokenStore codefTokenStore,
            @Qualifier("codefRestTemplate") RestTemplate restTemplate,
            @Qualifier("codefObjectMapper") ObjectMapper objectMapper
    ) {
        this.codefProperties = codefProperties;
        this.codefTokenStore = codefTokenStore;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getValidAccessToken() {
        validateCredentials();
        LocalDateTime now = LocalDateTime.now();

        return findUsableToken(now)
                .map(CodefToken::getAccessToken)
                .orElseGet(this::refreshExpiredToken);
    }

    @Override
    public synchronized String refreshAccessToken() {
        validateCredentials();
        return issueAndSaveToken(LocalDateTime.now());
    }

    private synchronized String refreshExpiredToken() {
        LocalDateTime now = LocalDateTime.now();
        return findUsableToken(now)
                .map(CodefToken::getAccessToken)
                .orElseGet(() -> issueAndSaveToken(now));
    }

    private java.util.Optional<CodefToken> findUsableToken(LocalDateTime now) {
        return codefTokenStore.findLatest(codefProperties.getServiceType(), codefProperties.getClientId())
                .filter(token -> token.isUsableAt(now, TOKEN_REFRESH_SKEW));
    }

    private String issueAndSaveToken(LocalDateTime now) {
        CodefTokenResponse response = requestToken();
        validateResponse(response);

        CodefToken token = new CodefToken();
        token.setServiceType(codefProperties.getServiceType().name());
        token.setClientId(codefProperties.getClientId());
        token.setAccessToken(response.getAccessToken());
        token.setExpiresAt(now.plusSeconds(response.getExpiresIn()));
        codefTokenStore.save(token);

        return token.getAccessToken();
    }

    private CodefTokenResponse requestToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.AUTHORIZATION, basicAuthHeader());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        String rawResponse = restTemplate.postForObject(TOKEN_URL, request, String.class);
        try {
            String decoded = URLDecoder.decode(rawResponse, StandardCharsets.UTF_8);
            return objectMapper.readValue(decoded, CodefTokenResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("CODEF 토큰 응답 파싱 실패", e);
        }
    }

    private String basicAuthHeader() {
        String credentials = codefProperties.getClientId() + ":" + codefProperties.getClientSecret();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private void validateResponse(CodefTokenResponse response) {
        if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()
                || response.getExpiresIn() == null || response.getExpiresIn() <= 0) {
            throw new IllegalStateException("CODEF 토큰 응답에 필수 값이 없습니다");
        }
    }

    private void validateCredentials() {
        if (codefProperties.getClientId() == null || codefProperties.getClientId().isBlank()
                || codefProperties.getClientSecret() == null || codefProperties.getClientSecret().isBlank()) {
            throw new IllegalStateException("CODEF client-id/client-secret 설정이 비어 있습니다");
        }
    }
}
