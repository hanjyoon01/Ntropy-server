package com.ntropy.user.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    // 테스트 전에 JwtProvider 인스턴스를 초기화합니다.
    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider("testSecretKeytestSecretKeytestSecretKeytestSecretKey", 3600000L, 1209600000L); // 1시간, 2주
    }

    @Test
    @DisplayName("Access Token 생성 및 검증 성공")
    void createAndValidateAccessToken_success() {
        String userId = "1";
        String email = "test@example.com";
        String role = "ROLE_USER";

        String accessToken = jwtProvider.createAccessToken(userId, email, role);

        assertThat(accessToken).isNotNull();
        assertThat(jwtProvider.validateToken(accessToken)).isTrue();
        assertThat(jwtProvider.getUserId(accessToken)).isEqualTo(userId);
        assertThat(jwtProvider.getRole(accessToken)).isEqualTo(role);
    }

    @Test
    @DisplayName("Refresh Token 생성 및 검증 성공")
    void createAndValidateRefreshToken_success() {
        String userId = "1";

        String refreshToken = jwtProvider.createRefreshToken(userId);

        assertThat(refreshToken).isNotNull();
        assertThat(jwtProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtProvider.getUserId(refreshToken)).isEqualTo(userId);
        assertThat(jwtProvider.getRole(refreshToken)).isNull(); // Refresh Token에는 role이 없음
    }

    @Test
    @DisplayName("만료된 토큰 검증 실패")
    void validateToken_expired() {
        // 만료 시간을 매우 짧게 설정하여 만료된 토큰 생성
        JwtProvider expiredJwtProvider = new JwtProvider("testSecretKeytestSecretKeytestSecretKeytestSecretKey", 1L, 1L);

        String expiredToken = expiredJwtProvider.createAccessToken("1", "test@example.com", "ROLE_USER");

        try {
            Thread.sleep(50); // 토큰 만료를 기다림
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(expiredJwtProvider.validateToken(expiredToken)).isFalse();
        // validateToken은 false를 반환하지만, getUserId 등은 ExpiredJwtException을 던질 수 있음
        assertThrows(ExpiredJwtException.class, () -> expiredJwtProvider.getUserId(expiredToken));
    }

    @Test
    @DisplayName("잘못된 서명 토큰 검증 실패")
    void validateToken_invalidSignature() {
        String validToken = jwtProvider.createAccessToken("1", "test@example.com", "ROLE_USER");
        // 서명이 다른 토큰 (가짜 토큰)
        String invalidSignatureToken = validToken + "invalid";

        assertThat(jwtProvider.validateToken(invalidSignatureToken)).isFalse();
        assertThrows(SignatureException.class, () -> jwtProvider.getUserId(invalidSignatureToken));
    }

    @Test
    @DisplayName("잘못된 형식의 토큰 검증 실패")
    void validateToken_malformed() {
        String malformedToken = "invalid.token.format";

        assertThat(jwtProvider.validateToken(malformedToken)).isFalse();
        assertThrows(MalformedJwtException.class, () -> jwtProvider.getUserId(malformedToken));
    }
}
