package com.ntropy.user.client.oauth;

import com.ntropy.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

// 구글과 통신하는 클라이언트
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private final RestTemplate restTemplate;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    @Value("${spring.security.oauth2.client.provider.google.token-uri}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String userInfoUri;


    // 인증 코드를 사용하여 구글 서버로부터 Access Token을 발급받음

    public String getAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUri, request, Map.class);
            return (String) response.getBody().get("access_token");
        } catch (Exception e) {
            log.error("구글 Access Token 발급 실패: {}", e.getMessage(), e);
            throw new RuntimeException("구글 Access Token 발급에 실패했습니다.", e);
        }
    }


    // 사용자 정보 조회
    public User getGoogleUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(userInfoUri, HttpMethod.GET, request, Map.class);
            Map<String, Object> userInfo = response.getBody();

            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String providerId = (String) userInfo.get("sub"); // 구글은 providerId 필드명이 'sub'

            return User.builder()
                    .email(email)
                    .name(name)
                    .providerId(providerId)
                    .provider("GOOGLE")
                    .build();
        } catch (Exception e) {
            log.error("구글 사용자 정보 조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("구글 사용자 정보 조회에 실패했습니다.", e);
        }
    }
}